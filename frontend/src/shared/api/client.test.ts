import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { delay, http, HttpResponse } from "msw";
import { server } from "../../test/server";
import {
  branchFixture,
  csrfFixture,
  identityFixture,
} from "../../test/identity-fixtures";
import { createApiClient } from "./client";
import type { ApiClient } from "./client";
import { createIdentityApi } from "./identity";
import { ApiError } from "./errors";

const origin = globalThis.location.origin;
const path = (route: string) => `${origin}/api/v1/${route}`;
const credentials = {
  identifier: "operador-prueba",
  password: " synthetic password ",
};
let client: ApiClient;
let api: ReturnType<typeof createIdentityApi>;

beforeEach(() => {
  client = createApiClient();
  api = createIdentityApi(client);
  server.use(http.get(path("auth/csrf"), () => HttpResponse.json(csrfFixture)));
});
afterEach(() => client.reset());

describe("single HTTP transport", () => {
  it("prepares CSRF before login, keeps password bytes and handles 204 without JSON", async () => {
    const seen: string[] = [];
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    server.use(
      http.get(path("auth/csrf"), ({ request }) => {
        seen.push("csrf");
        expect(request.credentials).toBe("same-origin");
        return HttpResponse.json(csrfFixture);
      }),
      http.post(path("auth/login"), async ({ request }) => {
        seen.push("login");
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(request.credentials).toBe("same-origin");
        expect(await request.json()).toEqual(credentials);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(api.login(credentials)).resolves.toBeUndefined();
    expect(seen).toEqual(["csrf", "login"]);
    // MSW's intercepted Request does not preserve every Fetch option.
    // Inspect the actual boundary arguments for redirect/mode/cache guarantees.
    expect(fetchSpy).toHaveBeenLastCalledWith(
      expect.any(URL),
      expect.objectContaining({
        credentials: "same-origin",
        redirect: "error",
        mode: "same-origin",
        cache: "no-store",
      }),
    );
  });

  it("does not attach CSRF to reads and projects only validated identity fields", async () => {
    server.use(
      http.get(path("auth/me"), ({ request }) => {
        expect(request.headers.has(csrfFixture.headerName)).toBe(false);
        return HttpResponse.json({
          ...identityFixture,
          internalValue: "never expose",
        });
      }),
    );
    await expect(api.currentUser()).resolves.toEqual(identityFixture);
  });

  it("reads the actual branch-context names and nullable active branch", async () => {
    const context = {
      organizationId: branchFixture.organizationId,
      scopeType: "BRANCH",
      activeBranch: branchFixture,
      availableBranches: [branchFixture],
    };
    server.use(
      http.get(path("me/branch-context"), () => HttpResponse.json(context)),
    );
    await expect(api.branchContext()).resolves.toEqual(context);
  });

  it("sends only the password contract fields and validates the reauthentication result", async () => {
    const body = {
      currentPassword: " synthetic current ",
      newPassword: " synthetic replacement ",
      newPasswordConfirmation: " synthetic replacement ",
    };
    const result = {
      userId: identityFixture.id,
      profileVersion: 4,
      reauthenticationRequired: true,
    };
    server.use(
      http.post(path("me/profile/password"), async ({ request }) => {
        expect(await request.json()).toEqual(body);
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        return HttpResponse.json(result);
      }),
    );
    await expect(
      api.changePassword({ ...body, ...{ userId: "must not be submitted" } }),
    ).resolves.toEqual(result);
  });

  it("bootstraps logout protection without a credential body", async () => {
    server.use(
      http.post(path("auth/logout"), async ({ request }) => {
        expect(await request.text()).toBe("");
        expect(request.headers.has("Content-Type")).toBe(false);
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(api.logout()).resolves.toBeUndefined();
  });

  it.each([
    "https://untrusted.invalid/api/v1/auth/login",
    "//untrusted.invalid/api/v1/auth/login",
    "/api/v1/../auth/login",
    "/api/v1/%2e%2e/auth/login",
    "/api/v1/auth/login?password=synthetic",
    "/api/v1/auth/login#fragment",
    "/api/v1/branches",
    "auth/login",
  ])(
    "rejects an unapproved URL before fetching or obtaining CSRF: %s",
    async (url) => {
      const fetchSpy = vi.spyOn(globalThis, "fetch");
      await expect(
        client.request({
          path: url,
          method: "POST",
          expectedStatus: 204,
          parse: () => undefined,
          body: credentials,
        }),
      ).rejects.toMatchObject({ kind: "invalid-request" });
      expect(fetchSpy).not.toHaveBeenCalled();
    },
  );

  it("rejects a verb outside F01 before sending credentials", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    await expect(
      client.request({
        path: "/api/v1/me/branch-context",
        method: "POST",
        expectedStatus: 204,
        parse: () => undefined,
      }),
    ).rejects.toMatchObject({ kind: "invalid-request" });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it.each([
    {},
    { ...identityFixture, id: "invalid" },
    { ...identityFixture, roles: "ADMIN" },
    { ...identityFixture, passwordChangeRequired: "false" },
    { ...identityFixture, availableBranches: [{}] },
    { ...identityFixture, organizationScope: null },
  ])("rejects malformed identity at runtime", async (body) => {
    server.use(http.get(path("auth/me"), () => HttpResponse.json(body)));
    await expect(api.currentUser()).rejects.toMatchObject({
      kind: "invalid-response",
    });
  });

  it.each([
    ["application/json", "{broken"],
    ["text/html", "<html>Internal path</html>"],
    ["application/json", "null"],
    ["application/json", "[]"],
    ["application/json", ""],
  ])("rejects unexpected success content: %s", async (contentType, body) => {
    server.use(
      http.get(
        path("auth/me"),
        () =>
          new HttpResponse(body, { headers: { "Content-Type": contentType } }),
      ),
    );
    await expect(api.currentUser()).rejects.toMatchObject({
      kind: "invalid-response",
    });
  });

  it("rejects an unexpected successful HTTP status", async () => {
    server.use(
      http.get(path("auth/me"), () => new HttpResponse(null, { status: 204 })),
    );
    await expect(api.currentUser()).rejects.toMatchObject({
      kind: "invalid-response",
    });
  });

  it("distinguishes a network failure without preserving its raw cause", async () => {
    server.use(http.get(path("auth/me"), () => HttpResponse.error()));
    const error = await api.currentUser().catch((failure: unknown) => failure);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: "network", status: undefined });
    expect(error).not.toHaveProperty("cause");
  });

  it.each(["login", "logout", "password"])(
    "never replays a %s mutation after a network failure",
    async (operation) => {
      const route =
        operation === "password" ? "me/profile/password" : `auth/${operation}`;
      let submitted = 0;
      server.use(
        http.post(path(route), () => {
          submitted++;
          return HttpResponse.error();
        }),
      );
      const pending =
        operation === "login"
          ? api.login(credentials)
          : operation === "logout"
            ? api.logout()
            : api.changePassword({
                currentPassword: "synthetic current",
                newPassword: "synthetic replacement",
                newPasswordConfirmation: "synthetic replacement",
              });
      await expect(pending).rejects.toMatchObject({ kind: "network" });
      expect(submitted).toBe(1);
    },
  );

  it("rejects a malformed password-change result without treating it as confirmed", async () => {
    server.use(
      http.post(path("me/profile/password"), () =>
        HttpResponse.json({
          userId: identityFixture.id,
          profileVersion: "4",
          reauthenticationRequired: true,
        }),
      ),
    );
    await expect(
      api.changePassword({
        currentPassword: "synthetic current",
        newPassword: "synthetic replacement",
        newPasswordConfirmation: "synthetic replacement",
      }),
    ).rejects.toMatchObject({ kind: "invalid-response" });
  });

  it.each([0, -1, Infinity, NaN, 2_147_483_648])(
    "rejects an invalid timeout: %s",
    (timeoutMs) => {
      expect(() => createApiClient({ timeoutMs })).toThrow(ApiError);
    },
  );
});

describe("CSRF lifecycle and concurrency", () => {
  it("deduplicates simultaneous bootstrap and mutations", async () => {
    let bootstrap = 0;
    let mutations = 0;
    server.use(
      http.get(path("auth/csrf"), async () => {
        bootstrap++;
        await delay(25);
        return HttpResponse.json(csrfFixture);
      }),
      http.post(path("auth/logout"), () => {
        mutations++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await Promise.all([client.getCsrf(), api.logout(), api.logout()]);
    expect(bootstrap).toBe(1);
    expect(mutations).toBe(2);
  });

  it("uses the server-provided header name", async () => {
    server.use(
      http.get(path("auth/csrf"), () =>
        HttpResponse.json({ ...csrfFixture, headerName: "X-CSRF-TOKEN" }),
      ),
      http.post(path("auth/logout"), ({ request }) => {
        expect(request.headers.get("X-CSRF-TOKEN")).toBe(csrfFixture.token);
        expect(request.headers.has("X-XSRF-TOKEN")).toBe(false);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await api.logout();
  });

  it.each([
    {},
    { ...csrfFixture, token: "" },
    { ...csrfFixture, token: "unsafe\r\nvalue" },
    { ...csrfFixture, headerName: "Authorization" },
    { ...csrfFixture, headerName: "Cookie" },
    { ...csrfFixture, headerName: "X-Bad\r\nvalue" },
  ])("rejects an unsafe CSRF response before mutating", async (body) => {
    let submitted = 0;
    server.use(
      http.get(path("auth/csrf"), () => HttpResponse.json(body)),
      http.post(path("auth/login"), () => {
        submitted++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(api.login(credentials)).rejects.toMatchObject({
      kind: "invalid-response",
    });
    expect(submitted).toBe(0);
  });

  it("renews after CSRF rejection, without replaying; the next explicit attempt uses the new token", async () => {
    let bootstrap = 0;
    let submitted = 0;
    server.use(
      http.get(path("auth/csrf"), () =>
        HttpResponse.json({
          ...csrfFixture,
          token: `synthetic-csrf-${++bootstrap}`,
        }),
      ),
      http.post(path("auth/login"), ({ request }) => {
        submitted++;
        if (submitted === 1)
          return HttpResponse.json(
            { status: 403, code: "CSRF_TOKEN_INVALID" },
            { status: 403 },
          );
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          "synthetic-csrf-2",
        );
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(api.login(credentials)).rejects.toMatchObject({
      code: "CSRF_TOKEN_INVALID",
    });
    expect(bootstrap).toBe(2);
    expect(submitted).toBe(1);
    await api.login(credentials);
    expect(submitted).toBe(2);
  });

  it("preserves a CSRF rejection when renewal is unavailable", async () => {
    let bootstrap = 0;
    server.use(
      http.get(path("auth/csrf"), () =>
        ++bootstrap === 1
          ? HttpResponse.json(csrfFixture)
          : HttpResponse.error(),
      ),
      http.post(path("auth/logout"), () =>
        HttpResponse.json(
          { status: 403, code: "CSRF_TOKEN_INVALID" },
          { status: 403 },
        ),
      ),
    );
    await expect(api.logout()).rejects.toMatchObject({
      code: "CSRF_TOKEN_INVALID",
    });
    expect(bootstrap).toBe(2);
  });

  it("does not discard a renewed token when an older concurrent mutation is rejected later", async () => {
    let bootstrap = 0;
    let submitted = 0;
    let releaseSecond: (() => void) | undefined;
    const secondResponse = new Promise<void>((resolve) => {
      releaseSecond = resolve;
    });
    server.use(
      http.get(path("auth/csrf"), () =>
        HttpResponse.json({
          ...csrfFixture,
          token: `synthetic-csrf-${++bootstrap}`,
        }),
      ),
      http.post(path("auth/login"), async () => {
        submitted++;
        if (submitted === 2) await secondResponse;
        return HttpResponse.json(
          { status: 403, code: "CSRF_TOKEN_INVALID" },
          { status: 403 },
        );
      }),
    );
    const first = api.login(credentials).catch((error: unknown) => error);
    const second = api.login(credentials).catch((error: unknown) => error);
    await expect(first).resolves.toMatchObject({ code: "CSRF_TOKEN_INVALID" });
    releaseSecond?.();
    await expect(second).resolves.toMatchObject({ code: "CSRF_TOKEN_INVALID" });
    expect(bootstrap).toBe(2);
    expect(submitted).toBe(2);
    expect((await client.getCsrf()).token).toBe("synthetic-csrf-2");
  });

  it("invalidates CSRF across a successful session transition", async () => {
    let bootstrap = 0;
    server.use(
      http.get(path("auth/csrf"), () => {
        bootstrap++;
        return HttpResponse.json(csrfFixture);
      }),
      http.post(
        path("auth/login"),
        () => new HttpResponse(null, { status: 204 }),
      ),
    );
    await client.getCsrf();
    await api.login(credentials);
    await client.getCsrf();
    expect(bootstrap).toBe(2);
  });

  it("does not interpret the cookie as the CSRF contract", async () => {
    document.cookie = "XSRF-TOKEN=synthetic-cookie-only";
    server.use(http.get(path("auth/csrf"), () => HttpResponse.json({})));
    await expect(api.login(credentials)).rejects.toMatchObject({
      kind: "invalid-response",
    });
    document.cookie = "XSRF-TOKEN=; Max-Age=0";
  });

  it("keeps the cached token immutable", async () => {
    expect(Object.isFrozen(await client.getCsrf())).toBe(true);
  });
});

describe("cancellation and late responses", () => {
  it("does not fetch when already cancelled", async () => {
    const controller = new AbortController();
    controller.abort();
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    await expect(
      api.currentUser({ signal: controller.signal }),
    ).rejects.toMatchObject({ kind: "cancelled" });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("cancels one waiter without cancelling another shared bootstrap", async () => {
    const controller = new AbortController();
    let started: (() => void) | undefined;
    const entered = new Promise<void>((resolve) => {
      started = resolve;
    });
    let bootstrap = 0;
    server.use(
      http.get(path("auth/csrf"), async () => {
        bootstrap++;
        started?.();
        await delay(40);
        return HttpResponse.json(csrfFixture);
      }),
    );
    const first = client
      .getCsrf(controller.signal)
      .catch((error: unknown) => error);
    const second = client.getCsrf();
    await entered;
    controller.abort();
    await expect(first).resolves.toMatchObject({ kind: "cancelled" });
    await expect(second).resolves.toEqual(csrfFixture);
    expect(bootstrap).toBe(1);
  });

  it("bounds a slow request by timeout and does not retry it", async () => {
    client.reset();
    client = createApiClient({ timeoutMs: 100 });
    api = createIdentityApi(client);
    let reads = 0;
    server.use(
      http.get(path("auth/me"), async () => {
        reads++;
        await delay(250);
        return HttpResponse.json(identityFixture);
      }),
    );
    await expect(api.currentUser()).rejects.toMatchObject({ kind: "timeout" });
    expect(reads).toBe(1);
  });

  it("does not submit a mutation when cancelled during CSRF bootstrap", async () => {
    const controller = new AbortController();
    let started: (() => void) | undefined;
    const entered = new Promise<void>((resolve) => {
      started = resolve;
    });
    let submitted = 0;
    server.use(
      http.get(path("auth/csrf"), async () => {
        started?.();
        await delay(40);
        return HttpResponse.json(csrfFixture);
      }),
      http.post(path("auth/login"), () => {
        submitted++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const pending = api
      .login(credentials, { signal: controller.signal })
      .catch((error: unknown) => error);
    await entered;
    controller.abort();
    await expect(pending).resolves.toMatchObject({ kind: "cancelled" });
    expect(submitted).toBe(0);
  });

  it("consumes the original rejection when cancellation happens synchronously at the Fetch boundary", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementationOnce(() => {
      client.reset();
      return Promise.reject(new Error("Synthetic cancelled network operation"));
    });
    await expect(api.currentUser()).rejects.toMatchObject({
      kind: "cancelled",
    });
  });

  it("reset prevents a late fetch from returning data even if fetch ignores cancellation", async () => {
    let finish: ((response: Response) => void) | undefined;
    vi.spyOn(globalThis, "fetch").mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
    const pending = api.currentUser().catch((error: unknown) => error);
    client.reset();
    finish?.(Response.json(identityFixture));
    await expect(pending).resolves.toMatchObject({ kind: "cancelled" });
  });

  it("reset drops an obsolete CSRF bootstrap and forces a new one", async () => {
    let finish: ((response: Response) => void) | undefined;
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
    const pending = client.getCsrf().catch((error: unknown) => error);
    client.reset();
    finish?.(Response.json({ ...csrfFixture, token: "synthetic-obsolete" }));
    await expect(pending).resolves.toMatchObject({ kind: "cancelled" });
    fetchSpy.mockRestore();
    await expect(client.getCsrf()).resolves.toEqual(csrfFixture);
  });
});
