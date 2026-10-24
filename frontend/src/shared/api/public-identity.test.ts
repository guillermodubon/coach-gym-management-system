import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { csrfFixture } from "../../test/identity-fixtures";
import { createApiClient } from "./client";
import { createPublicIdentityApi } from "./public-identity";
import type { ApiClient } from "./client";

let client: ApiClient;
let api: ReturnType<typeof createPublicIdentityApi>;

beforeEach(() => {
  client = createApiClient();
  api = createPublicIdentityApi(client);
  server.use(
    http.get("/api/v1/auth/csrf", () => HttpResponse.json(csrfFixture)),
  );
});
afterEach(() => client.reset());

describe("public staff identity HTTP API", () => {
  it("sends the recovery request with CSRF and validates only its 202 acknowledgement", async () => {
    server.use(
      http.post(
        "/api/v1/auth/password-recovery-requests",
        async ({ request }) => {
          expect(request.headers.get(csrfFixture.headerName)).toBe(
            csrfFixture.token,
          );
          expect(await request.json()).toEqual({ email: "staff@example.test" });
          return HttpResponse.json(
            { message: "If eligible, instructions will be sent." },
            { status: 202 },
          );
        },
      ),
    );
    await expect(
      api.requestPasswordRecovery({ email: "staff@example.test" }),
    ).resolves.toEqual({ message: "If eligible, instructions will be sent." });
  });

  it("completes recovery with exact secret fields and does not expect a body on 204", async () => {
    const credentials = {
      token: "synthetic-recovery-token",
      newPassword: "synthetic new password",
      passwordConfirmation: "synthetic new password",
    };
    server.use(
      http.post(
        "/api/v1/auth/password-recovery/complete",
        async ({ request }) => {
          expect(request.headers.get(csrfFixture.headerName)).toBe(
            csrfFixture.token,
          );
          expect(await request.json()).toEqual(credentials);
          return new HttpResponse(null, { status: 204 });
        },
      ),
    );
    await expect(
      api.completePasswordRecovery(credentials),
    ).resolves.toBeUndefined();
  });

  it("inspects an invitation only through the explicit CSRF-protected POST contract", async () => {
    const token = "synthetic-invitation-token";
    server.use(
      http.post("/api/v1/staff-invitations/inspect", async ({ request }) => {
        expect(request.method).toBe("POST");
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(await request.json()).toEqual({ token });
        return HttpResponse.json({
          invitationId: "550e8400-e29b-41d4-a716-446655440000",
          maskedEmail: "s***@example.test",
          proposedRole: "RECEPTIONIST",
          proposedScope: "BRANCH",
          proposedBranches: [
            {
              branchId: "550e8400-e29b-41d4-a716-446655440001",
              code: "CENTRAL",
              name: "Sucursal central",
            },
          ],
          expiresAt: "2026-10-21T12:00:00Z",
        });
      }),
    );
    await expect(api.inspectStaffInvitation(token)).resolves.toMatchObject({
      maskedEmail: "s***@example.test",
      proposedRole: "RECEPTIONIST",
      proposedBranches: [{ code: "CENTRAL", name: "Sucursal central" }],
    });
  });

  it("accepts only invitation credentials and returns no authenticated session", async () => {
    const requestBody = {
      token: "synthetic-invitation-token",
      password: "synthetic password",
      passwordConfirmation: "synthetic password",
      firstName: "Ana",
      lastName: "Ríos",
    };
    const localBefore = { ...localStorage };
    const sessionBefore = { ...sessionStorage };
    server.use(
      http.post("/api/v1/staff-invitations/accept", async ({ request }) => {
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(await request.json()).toEqual(requestBody);
        return HttpResponse.json(
          {
            userId: "550e8400-e29b-41d4-a716-446655440002",
            role: "RECEPTIONIST",
            scope: "BRANCH",
            branchIds: ["550e8400-e29b-41d4-a716-446655440001"],
            acceptedAt: "2026-10-20T12:00:00Z",
          },
          { status: 201 },
        );
      }),
    );
    await expect(api.acceptStaffInvitation(requestBody)).resolves.toMatchObject(
      {
        role: "RECEPTIONIST",
        scope: "BRANCH",
      },
    );
    expect({ ...localStorage }).toEqual(localBefore);
    expect({ ...sessionStorage }).toEqual(sessionBefore);
  });
});
