import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { csrfFixture } from "../../test/identity-fixtures";
import { createApiClient } from "./client";
import { createProfileApi } from "./profile";
import { ApiError } from "./errors";
import type { ApiClient } from "./client";

const profilePath = "/api/v1/me/profile";
const photoPath = "/api/v1/me/profile/photo";
const profile = {
  userId: "550e8400-e29b-41d4-a716-446655440000",
  username: "operador-prueba",
  email: "operator@example.test",
  firstName: "Ana",
  lastName: "Ríos",
  displayName: "Ana Ríos",
  roles: ["ADMIN"],
  status: "ACTIVE",
  photoPresent: false,
  photoUrl: null,
  version: 4,
};
let client: ApiClient;
let api: ReturnType<typeof createProfileApi>;

beforeEach(() => {
  client = createApiClient();
  api = createProfileApi(client);
  server.use(
    http.get("/api/v1/auth/csrf", () => HttpResponse.json(csrfFixture)),
  );
});
afterEach(() => client.reset());

describe("staff profile HTTP API", () => {
  it("reads only the current actor profile and validates its response", async () => {
    server.use(
      http.get(profilePath, ({ request }) => {
        expect(new URL(request.url).search).toBe("");
        return HttpResponse.json(profile);
      }),
    );
    await expect(api.getProfile()).resolves.toEqual(profile);
  });

  it("updates only names and version with CSRF protection", async () => {
    server.use(
      http.put(profilePath, async ({ request }) => {
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(await request.json()).toEqual({
          firstName: "Ana María",
          lastName: "Ríos",
          version: 4,
        });
        return HttpResponse.json({ ...profile, firstName: "Ana María" });
      }),
    );
    await expect(
      api.updateProfile({
        firstName: "Ana María",
        lastName: "Ríos",
        version: 4,
      }),
    ).resolves.toMatchObject({ firstName: "Ana María", version: 4 });
  });

  it("renews CSRF after a rejected PUT without replaying the profile update", async () => {
    let csrfRequests = 0;
    let profileRequests = 0;
    server.use(
      http.get("/api/v1/auth/csrf", () =>
        HttpResponse.json({
          ...csrfFixture,
          token: `synthetic-csrf-${++csrfRequests}`,
        }),
      ),
      http.put(profilePath, ({ request }) => {
        profileRequests++;
        if (profileRequests === 1) {
          return HttpResponse.json(
            { status: 403, code: "CSRF_TOKEN_INVALID" },
            { status: 403 },
          );
        }
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          "synthetic-csrf-2",
        );
        return HttpResponse.json(profile);
      }),
    );
    const update = { firstName: "Ana", lastName: "Ríos", version: 4 };
    await expect(api.updateProfile(update)).rejects.toMatchObject({
      code: "CSRF_TOKEN_INVALID",
    });
    expect(profileRequests).toBe(1);
    expect(csrfRequests).toBe(2);
    await expect(api.updateProfile(update)).resolves.toEqual(profile);
    expect(profileRequests).toBe(2);
  });

  it("uploads a bounded photo as multipart with a neutral filename and no manual content type", async () => {
    const file = new File(
      ["synthetic image bytes"],
      "private-user-filename.png",
      {
        type: "image/png",
      },
    );
    const metadata = {
      photoId: "550e8400-e29b-41d4-a716-446655440001",
      contentType: "image/png",
      sizeBytes: file.size,
      updatedAt: "2026-10-20T12:00:00Z",
      version: 5,
    };
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const requestUrl = new URL(
        input instanceof Request ? input.url : String(input),
      );
      if (requestUrl.pathname === "/api/v1/auth/csrf") {
        return new Response(JSON.stringify(csrfFixture), {
          headers: { "Content-Type": "application/json" },
        });
      }
      expect(requestUrl.pathname).toBe(photoPath);
      expect(init?.method).toBe("PUT");
      expect(new Headers(init?.headers).get(csrfFixture.headerName)).toBe(
        csrfFixture.token,
      );
      expect(new Headers(init?.headers).has("Content-Type")).toBe(false);
      expect(init?.body).toBeInstanceOf(FormData);
      const body = init?.body as FormData;
      const uploaded = body.get("file");
      expect(uploaded).toBeInstanceOf(File);
      expect((uploaded as File).name).toBe("profile-photo");
      expect((uploaded as File).type).toBe("image/png");
      expect(body.get("version")).toBe("4");
      return new Response(JSON.stringify(metadata), {
        headers: { "Content-Type": "application/json" },
      });
    });
    await expect(api.uploadPhoto(file, 4)).resolves.toEqual(metadata);
  });

  it("rejects invalid files and versions before obtaining CSRF or fetching", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    expect(() =>
      api.uploadPhoto(
        new File(["x"], "private.svg", { type: "image/svg+xml" }),
        0,
      ),
    ).toThrowError(ApiError);
    expect(() => api.removePhoto(-1)).toThrowError(ApiError);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("downloads an allowed private image as a bounded Blob", async () => {
    server.use(
      http.get(
        photoPath,
        () =>
          new HttpResponse(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), {
            headers: {
              "Content-Type": "image/png",
              "Cache-Control": "private, no-store",
            },
          }),
      ),
    );
    const blob = await api.downloadPhoto();
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe("image/png");
    expect(blob.size).toBe(4);
  });

  it("rejects unsupported or over-limit binary responses", async () => {
    server.use(
      http.get(
        photoPath,
        () =>
          new HttpResponse(new Uint8Array([1]), {
            headers: { "Content-Type": "image/svg+xml" },
          }),
      ),
    );
    await expect(api.downloadPhoto()).rejects.toMatchObject({
      kind: "invalid-response",
    });

    server.use(
      http.get(
        photoPath,
        () =>
          new HttpResponse(new Uint8Array(5 * 1024 * 1024 + 1), {
            headers: { "Content-Type": "image/png" },
          }),
      ),
    );
    await expect(api.downloadPhoto()).rejects.toMatchObject({
      kind: "invalid-response",
    });
  });

  it("converts a private 404 ProblemDetail before treating it as binary", async () => {
    server.use(
      http.get(photoPath, () =>
        HttpResponse.json(
          { status: 404, code: "STAFF_PROFILE_PHOTO_NOT_FOUND" },
          {
            status: 404,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );
    await expect(api.downloadPhoto()).rejects.toMatchObject({
      kind: "http",
      status: 404,
      code: "STAFF_PROFILE_PHOTO_NOT_FOUND",
    });
  });

  it("removes a photo with only the version query and no body", async () => {
    server.use(
      http.delete(photoPath, async ({ request }) => {
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(new URL(request.url).search).toBe("?version=4");
        expect(await request.text()).toBe("");
        return HttpResponse.json(profile);
      }),
    );
    await expect(api.removePhoto(4)).resolves.toEqual(profile);
  });
});
