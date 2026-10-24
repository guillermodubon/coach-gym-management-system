import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { ApiError, httpError } from "./errors";
import { createApiClient } from "./client";
import { createIdentityApi } from "./identity";

const url = `${globalThis.location.origin}/api/v1/auth/me`;

describe("safe ProblemDetail conversion", () => {
  it.each([
    [400, "VALIDATION_FAILED"],
    [400, "MALFORMED_REQUEST"],
    [400, "CURRENT_PASSWORD_INVALID"],
    [400, "STAFF_PROFILE_VALIDATION_FAILED"],
    [400, "STAFF_IDENTITY_REQUEST_INVALID"],
    [401, "INVALID_CREDENTIALS"],
    [401, "AUTHENTICATION_REQUIRED"],
    [403, "CSRF_TOKEN_INVALID"],
    [403, "ACCESS_DENIED"],
    [403, "STAFF_IDENTITY_OPERATION_FORBIDDEN"],
    [404, "STAFF_PROFILE_NOT_FOUND"],
    [404, "STAFF_PROFILE_PHOTO_NOT_FOUND"],
    [409, "ACTIVE_BRANCH_UNAVAILABLE"],
    [409, "STAFF_PROFILE_VERSION_CONFLICT"],
    [409, "PASSWORD_RECOVERY_NOT_AVAILABLE"],
    [409, "INVITATION_NOT_AVAILABLE"],
    [413, "STAFF_PROFILE_PHOTO_TOO_LARGE"],
    [500, "STAFF_PROFILE_STORAGE_FAILED"],
    [500, "STAFF_PROFILE_DATA_ACCESS_FAILED"],
    [500, "STAFF_IDENTITY_OPERATION_FAILED"],
    [429, "AUTHENTICATION_RATE_LIMITED"],
  ])("distinguishes HTTP %s and %s", async (status, code) => {
    server.use(
      http.get(url, () =>
        HttpResponse.json(
          { status, code, detail: "Raw internal data" },
          { status },
        ),
      ),
    );
    const client = createApiClient();
    try {
      await expect(
        createIdentityApi(client).currentUser(),
      ).rejects.toMatchObject({ kind: "http", status, code });
    } finally {
      client.reset();
    }
  });

  it.each([
    [500, null, undefined],
    [503, "<html>Internal path</html>", "text/html"],
    [502, "{broken", "application/problem+json"],
    [401, "", undefined],
  ])(
    "preserves HTTP %s when the body is absent or unusable",
    async (status, body, contentType) => {
      server.use(
        http.get(
          url,
          () =>
            new HttpResponse(body, {
              status,
              ...(contentType
                ? { headers: { "Content-Type": contentType } }
                : {}),
            }),
        ),
      );
      const client = createApiClient();
      try {
        await expect(
          createIdentityApi(client).currentUser(),
        ).rejects.toMatchObject({ kind: "http", status, code: undefined });
      } finally {
        client.reset();
      }
    },
  );

  it("keeps only known fields with local messages and bounded correlation metadata", () => {
    const privateValue = "synthetic-private-value-do-not-echo";
    const response = new Response(null, {
      status: 400,
      headers: { "X-Correlation-ID": "test-correlation-123" },
    });
    const error = httpError(response, {
      status: 400,
      code: "VALIDATION_FAILED",
      title: privateValue,
      detail: privateValue,
      instance: "/internal/path",
      trace: privateValue,
      fieldErrors: {
        password: privateValue,
        role: privateValue,
        identifier: privateValue,
        firstName: privateValue,
        email: privateValue,
        token: privateValue,
        file: privateValue,
      },
    });
    expect(error.fieldErrors).toEqual({
      password: "Revisa este campo.",
      identifier: "Revisa este campo.",
      firstName: "Revisa este campo.",
      email: "Revisa este campo.",
      token: "Revisa este campo.",
      file: "Revisa este campo.",
    });
    expect(error.correlationId).toBe("test-correlation-123");
    expect(error.message).not.toContain(privateValue);
    expect(JSON.stringify(error)).not.toContain(privateValue);
    expect(error).not.toHaveProperty("cause");
    expect(error).not.toHaveProperty("response");
    expect(error).not.toHaveProperty("body");
  });

  it("does not trust mismatched status, unknown codes or unsafe correlation values", () => {
    const error = httpError(new Response(null, { status: 403 }), {
      status: 401,
      code: "INVALID_CREDENTIALS",
      correlationId: "<unsafe>",
    });
    expect(error.code).toBeUndefined();
    expect(error.correlationId).toBeUndefined();
    expect(
      httpError(new Response(null, { status: 403 }), {
        status: 403,
        code: "INVALID_CREDENTIALS",
      }).code,
    ).toBeUndefined();
    expect(
      httpError(new Response(null, { status: 500 }), {
        status: 500,
        code: "UNREVIEWED_INTERNAL_VALUE",
      }).code,
    ).toBeUndefined();
  });

  it("uses a valid body correlation only when the header is unavailable", () => {
    expect(
      httpError(new Response(null, { status: 401 }), {
        status: 401,
        correlationId: "synthetic-correlation",
      }).correlationId,
    ).toBe("synthetic-correlation");
  });

  it("reads Retry-After seconds and dates only for 429; absence stays unknown", () => {
    const response = (value?: string) =>
      new Response(null, {
        status: 429,
        ...(value ? { headers: { "Retry-After": value } } : {}),
      });
    expect(httpError(response("12"), null).retryAfterSeconds).toBe(12);
    expect(httpError(response("0"), null).retryAfterSeconds).toBe(0);
    expect(httpError(response(), null).retryAfterSeconds).toBeUndefined();
    expect(httpError(response("-1"), null).retryAfterSeconds).toBeUndefined();
    expect(
      httpError(response("untrusted text"), null).retryAfterSeconds,
    ).toBeUndefined();
    expect(
      httpError(response("9999999999"), null).retryAfterSeconds,
    ).toBeUndefined();
    const future = new Date(Date.now() + 60_000).toUTCString();
    expect(httpError(response(future), null).retryAfterSeconds).toBeGreaterThan(
      0,
    );
    expect(
      httpError(
        new Response(null, { status: 403, headers: { "Retry-After": "12" } }),
        null,
      ).retryAfterSeconds,
    ).toBeUndefined();
  });

  it("does not preserve credentials in transport errors or browser storage", async () => {
    const client = createApiClient();
    const password = "synthetic-must-not-be-persisted";
    const localBefore = { ...localStorage };
    const sessionBefore = { ...sessionStorage };
    server.use(
      http.get(`${globalThis.location.origin}/api/v1/auth/csrf`, () =>
        HttpResponse.json({
          token: "synthetic-csrf",
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
        }),
      ),
      http.post(`${globalThis.location.origin}/api/v1/auth/login`, () =>
        HttpResponse.json(
          { status: 401, code: "INVALID_CREDENTIALS", detail: password },
          { status: 401 },
        ),
      ),
    );
    try {
      const error = await createIdentityApi(client)
        .login({ identifier: "synthetic-user", password })
        .catch((failure: unknown) => failure);
      expect(error).toBeInstanceOf(ApiError);
      expect(JSON.stringify(error)).not.toContain(password);
      expect({ ...localStorage }).toEqual(localBefore);
      expect({ ...sessionStorage }).toEqual(sessionBefore);
    } finally {
      client.reset();
    }
  });
});
