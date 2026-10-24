import { describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../../shared/api/client";
import type { CurrentUser } from "../../shared/api/contracts";
import { ApiError } from "../../shared/api/errors";
import { createQueryClient } from "../../shared/api/query-client";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { createAuthController } from "./auth-controller";
import type { AuthController } from "./auth-controller";
import { validateAuthority } from "./auth-model";
import type { SessionEvents, VisibilityEvents } from "./session-events";

function user(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return structuredClone({ ...identityFixture, ...overrides }) as CurrentUser;
}

function httpError(
  status: number,
  code:
    | "AUTHENTICATION_REQUIRED"
    | "INVALID_CREDENTIALS"
    | "ACTIVE_BRANCH_UNAVAILABLE"
    | "CSRF_TOKEN_INVALID"
    | "CURRENT_PASSWORD_INVALID"
    | "STAFF_PROFILE_VALIDATION_FAILED"
    | "STAFF_PROFILE_VERSION_CONFLICT"
    | "ACCESS_DENIED"
    | "AUTHENTICATION_RATE_LIMITED",
  options: { retryAfterSeconds?: number } = {},
) {
  return new ApiError("http", { status, code, ...options });
}

function makeHarness() {
  let currentTime = 10_000;
  let visible = true;
  const focusListeners = new Set<() => void>();
  const visibilityListeners = new Set<() => void>();
  let sessionListener: (() => void) | undefined;
  const visibilityEvents: VisibilityEvents = {
    addFocusListener: (listener) => focusListeners.add(listener),
    removeFocusListener: (listener) => focusListeners.delete(listener),
    addVisibilityListener: (listener) => visibilityListeners.add(listener),
    removeVisibilityListener: (listener) =>
      visibilityListeners.delete(listener),
    isVisible: () => visible,
  };
  const sessionEvents: SessionEvents = {
    subscribe: (listener) => {
      sessionListener = listener;
      return () => {
        sessionListener = undefined;
      };
    },
    publish: vi.fn(),
  };
  const getCsrf = vi.fn(async () => ({
    token: "synthetic-csrf",
    headerName: "X-XSRF-TOKEN",
    parameterName: "_csrf",
  }));
  const reset = vi.fn();
  const apiClient = {
    getCsrf,
    reset,
    request: vi.fn(),
  } as unknown as ApiClient;
  const currentUser = vi.fn(async () => user());
  const login = vi.fn(async () => undefined);
  const logoutRequest = vi.fn(async () => undefined);
  const identityApi = {
    currentUser,
    login,
    logout: logoutRequest,
    branchContext: vi.fn(),
    changePassword: vi.fn(),
  } as unknown as ReturnType<
    typeof import("../../shared/api/identity").createIdentityApi
  >;
  const queryClient = createQueryClient();
  const controller = createAuthController({
    apiClient,
    identityApi,
    queryClient,
    now: () => currentTime,
    visibilityEvents,
    sessionEvents,
  });

  return {
    controller,
    apiClient,
    getCsrf,
    currentUser,
    identityApi,
    login,
    logoutRequest,
    queryClient,
    reset,
    sessionEvents,
    setTime: (value: number) => {
      currentTime = value;
    },
    focus: () => focusListeners.forEach((listener) => listener()),
    setVisible: (value: boolean) => {
      visible = value;
      visibilityListeners.forEach((listener) => listener());
    },
    externalSessionChange: () => sessionListener?.(),
    dispose: () => queryClient.clear(),
  };
}

async function start(controller: AuthController): Promise<() => void> {
  const stop = controller.start();
  await controller.revalidate("initial");
  return stop;
}

async function startAnonymous(
  harness: ReturnType<typeof makeHarness>,
): Promise<() => void> {
  harness.currentUser.mockRejectedValueOnce(
    httpError(401, "AUTHENTICATION_REQUIRED"),
  );
  const stop = await start(harness.controller);
  expect(harness.controller.getSnapshot().state.kind).toBe("anonymous");
  return stop;
}

async function startPasswordRequired(
  harness: ReturnType<typeof makeHarness>,
): Promise<() => void> {
  harness.currentUser.mockResolvedValueOnce(
    user({ passwordChangeRequired: true }),
  );
  const stop = await start(harness.controller);
  expect(harness.controller.getSnapshot().state.kind).toBe("password-required");
  return stop;
}

describe("session authority validation", () => {
  it.each([
    ["ADMIN", "ORGANIZATION"],
    ["ADMIN", "BRANCH"],
    ["RECEPTIONIST", "BRANCH"],
  ] as const)("accepts %s with %s scope", (role, organizationScope) => {
    expect(
      validateAuthority(
        user({ roles: [role], organizationScope }) as CurrentUser,
      ),
    ).toEqual({ ok: true });
  });

  it.each([
    [[], "ORGANIZATION"],
    [["ADMIN", "ADMIN"], "ORGANIZATION"],
    [["ADMIN", "RECEPTIONIST"], "BRANCH"],
    [["MAINTENANCE"], "BRANCH"],
    [["RECEPTIONIST"], "ORGANIZATION"],
  ] as const)("rejects role/scope %j", (roles, organizationScope) => {
    expect(
      validateAuthority(
        user({ roles: [...roles], organizationScope }) as CurrentUser,
      ),
    ).toEqual({ ok: false, reason: "unsupported-authority" });
  });

  it("rejects inconsistent or empty branch scope", () => {
    expect(
      validateAuthority(
        user({ activeBranch: { ...branchFixture, name: "Otra" } }),
      ),
    ).toEqual({ ok: false, reason: "inconsistent-context" });
    expect(
      validateAuthority(
        user({ organizationScope: "BRANCH", availableBranches: [] }),
      ),
    ).toEqual({ ok: false, reason: "inconsistent-context" });
  });
});

describe("auth controller", () => {
  it("bootstraps the current session through one identity read", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: identityFixture.id },
    });
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    expect(harness.identityApi.branchContext).not.toHaveBeenCalled();
    stop();
    harness.dispose();
  });

  it("allows one extra identity read for an obsolete branch context", async () => {
    const harness = makeHarness();
    harness.currentUser
      .mockRejectedValueOnce(httpError(409, "ACTIVE_BRANCH_UNAVAILABLE"))
      .mockResolvedValueOnce(user());
    const stop = await start(harness.controller);
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    stop();
    harness.dispose();
  });

  it("restricts a persistent branch conflict without retaining identity", async () => {
    const harness = makeHarness();
    harness.currentUser.mockRejectedValue(
      httpError(409, "ACTIVE_BRANCH_UNAVAILABLE"),
    );
    const stop = await start(harness.controller);
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    expect(harness.controller.getSnapshot().state).toEqual({
      kind: "restricted",
      reason: "branch-context",
      sessionMayExist: true,
    });
    stop();
    harness.dispose();
  });

  it("classifies an unsupported server scope as restricted without exposing it", async () => {
    const harness = makeHarness();
    harness.currentUser.mockRejectedValueOnce(
      new ApiError("invalid-response", {
        clientReason: "unsupported-authority",
      }),
    );
    const stop = await start(harness.controller);
    expect(harness.controller.getSnapshot().state).toEqual({
      kind: "restricted",
      reason: "unsupported-authority",
      sessionMayExist: true,
    });
    expect(harness.controller.getSnapshot().error?.message).not.toContain(
      "REGION",
    );
    stop();
    harness.dispose();
  });

  it("reserves login synchronously and accepts only one concurrent ticket", async () => {
    const harness = makeHarness();
    const stop = await startAnonymous(harness);
    const firstTicket = harness.controller.beginCredentialAction("login");
    const secondTicket =
      await harness.controller.beginCredentialAction("login");
    expect(secondTicket).toBeNull();
    const ticket = await firstTicket;
    expect(ticket).not.toBeNull();
    expect(harness.controller.getSnapshot().pendingAction).toBe("login");
    stop();
    harness.dispose();
  });

  it("rechecks identity after login and never puts credentials in query keys", async () => {
    const harness = makeHarness();
    const stop = await startAnonymous(harness);
    const ticket = await harness.controller.beginCredentialAction("login");
    expect(ticket).not.toBeNull();
    await harness.controller.sendLogin(ticket!, {
      identifier: "synthetic-user",
      password: "synthetic-password",
    });
    await harness.controller.completeLogin(ticket!);
    expect(harness.login).toHaveBeenCalledTimes(1);
    expect(harness.getCsrf).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    expect(
      harness.queryClient
        .getQueryCache()
        .getAll()
        .flatMap(({ queryKey }) => queryKey)
        .join(" "),
    ).not.toContain("synthetic-password");
    stop();
    harness.dispose();
  });

  it("prioritizes mandatory-password notice and does not start other reads", async () => {
    const harness = makeHarness();
    harness.currentUser.mockResolvedValueOnce(
      user({ passwordChangeRequired: true }),
    );
    const stop = await start(harness.controller);
    expect(harness.controller.getSnapshot().state.kind).toBe(
      "password-required",
    );
    expect(harness.identityApi.branchContext).not.toHaveBeenCalled();
    stop();
    harness.dispose();
  });

  it("reserves one password ticket synchronously only for the required state", async () => {
    const anonymousHarness = makeHarness();
    const stopAnonymous = await startAnonymous(anonymousHarness);
    expect(
      await anonymousHarness.controller.beginCredentialAction("password"),
    ).toBeNull();
    stopAnonymous();
    anonymousHarness.dispose();

    const harness = makeHarness();
    const stop = await startPasswordRequired(harness);
    const firstPromise = harness.controller.beginCredentialAction("password");
    expect(harness.controller.getSnapshot().pendingAction).toBe("password");
    await expect(
      harness.controller.beginCredentialAction("password"),
    ).resolves.toBeNull();
    const ticket = await firstPromise;
    expect(ticket).toMatchObject({
      kind: "password",
      expectedUserId: identityFixture.id,
    });
    await harness.controller.logout();
    expect(harness.logoutRequest).not.toHaveBeenCalled();
    stop();
    harness.dispose();
  });

  it("reserves one actor-bound voluntary password ticket from an authenticated session", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);

    const firstPromise = harness.controller.beginCredentialAction("password");
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "authenticated", user: { id: identityFixture.id } },
      pendingAction: "password",
      privacyCovered: true,
    });
    await expect(
      harness.controller.beginCredentialAction("password"),
    ).resolves.toBeNull();

    const ticket = await firstPromise;
    expect(ticket).toMatchObject({
      kind: "password",
      expectedUserId: identityFixture.id,
      passwordMode: "voluntary",
    });
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it("requires a new login after a valid password-change response", async () => {
    const harness = makeHarness();
    const stop = await startPasswordRequired(harness);
    const ticket = await harness.controller.beginCredentialAction("password");
    expect(ticket).not.toBeNull();

    await harness.controller.completePasswordChange(ticket!, {
      userId: identityFixture.id,
      profileVersion: 8,
      reauthenticationRequired: true,
    });

    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      pendingAction: null,
      notice: "password-changed-login-again",
    });
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    expect(harness.login).not.toHaveBeenCalled();
    expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it.each([
    ["different account", "other-user", true],
    ["no reauthentication", identityFixture.id, false],
  ])(
    "treats a 200 response with %s as uncertain",
    async (_case, userId, reauthenticationRequired) => {
      const harness = makeHarness();
      const stop = await startPasswordRequired(harness);
      const ticket = await harness.controller.beginCredentialAction("password");
      await harness.controller.completePasswordChange(ticket!, {
        userId,
        profileVersion: 8,
        reauthenticationRequired,
      });

      expect(harness.controller.getSnapshot().state).toMatchObject({
        kind: "unavailable",
        reason: "password-unknown",
      });
      expect(harness.currentUser).toHaveBeenCalledTimes(1);
      expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
      stop();
      harness.dispose();
    },
  );

  it.each([
    [400, "CURRENT_PASSWORD_INVALID"],
    [400, "STAFF_PROFILE_VALIDATION_FAILED"],
    [409, "STAFF_PROFILE_VERSION_CONFLICT"],
    [403, "CSRF_TOKEN_INVALID"],
    [429, "AUTHENTICATION_RATE_LIMITED"],
  ] as const)(
    "keeps voluntary mode after recoverable %i %s",
    async (status, code) => {
      const harness = makeHarness();
      const stop = await start(harness.controller);
      const ticket = await harness.controller.beginCredentialAction("password");
      const error = httpError(
        status,
        code,
        status === 429 ? { retryAfterSeconds: 2 } : {},
      );
      await harness.controller.failPasswordChange(ticket!, error);

      expect(harness.controller.getSnapshot()).toMatchObject({
        state: { kind: "authenticated", user: { id: identityFixture.id } },
        pendingAction: null,
        error: { code },
        privacyCovered: false,
      });
      expect(harness.currentUser).toHaveBeenCalledTimes(1);
      stop();
      harness.dispose();
    },
  );

  it("requires fresh authentication after a voluntary password change without logout", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    const ticket = await harness.controller.beginCredentialAction("password");

    await harness.controller.completePasswordChange(ticket!, {
      userId: identityFixture.id,
      profileVersion: 8,
      reauthenticationRequired: true,
    });

    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      pendingAction: null,
      notice: "password-changed-login-again",
    });
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    expect(harness.logoutRequest).not.toHaveBeenCalled();
    expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it("keeps uncertain voluntary password changes manual until session revalidation", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    const ticket = await harness.controller.beginCredentialAction("password");
    await harness.controller.failPasswordChange(
      ticket!,
      new ApiError("timeout"),
    );

    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "unavailable",
      reason: "password-unknown",
    });
    harness.focus();
    harness.externalSessionChange();
    expect(harness.currentUser).toHaveBeenCalledTimes(1);

    await harness.controller.revalidate("manual");
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    stop();
    harness.dispose();
  });

  it.each([
    [400, "CURRENT_PASSWORD_INVALID"],
    [400, "STAFF_PROFILE_VALIDATION_FAILED"],
    [409, "STAFF_PROFILE_VERSION_CONFLICT"],
    [403, "CSRF_TOKEN_INVALID"],
    [429, "AUTHENTICATION_RATE_LIMITED"],
  ] as const)(
    "keeps the mandatory gate after recoverable %i %s",
    async (status, code) => {
      const harness = makeHarness();
      const stop = await startPasswordRequired(harness);
      const ticket = await harness.controller.beginCredentialAction("password");
      const error = httpError(
        status,
        code,
        status === 429 ? { retryAfterSeconds: 2 } : {},
      );
      await harness.controller.failPasswordChange(ticket!, error);

      expect(harness.controller.getSnapshot()).toMatchObject({
        state: { kind: "password-required", user: { id: identityFixture.id } },
        pendingAction: null,
        error: { code },
      });
      expect(harness.currentUser).toHaveBeenCalledTimes(1);
      if (status === 429) {
        await expect(
          harness.controller.beginCredentialAction("password"),
        ).resolves.toBeNull();
      }
      stop();
      harness.dispose();
    },
  );

  it("routes an expired password-change session back to login", async () => {
    const harness = makeHarness();
    const stop = await startPasswordRequired(harness);
    const ticket = await harness.controller.beginCredentialAction("password");
    await harness.controller.failPasswordChange(
      ticket!,
      httpError(401, "AUTHENTICATION_REQUIRED"),
    );

    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      pendingAction: null,
      notice: "session-ended",
    });
    expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it("restricts non-CSRF 403 responses without retaining account data", async () => {
    const harness = makeHarness();
    const stop = await startPasswordRequired(harness);
    const ticket = await harness.controller.beginCredentialAction("password");
    await harness.controller.failPasswordChange(
      ticket!,
      httpError(403, "ACCESS_DENIED"),
    );

    expect(harness.controller.getSnapshot().state).toEqual({
      kind: "restricted",
      reason: "forbidden",
      sessionMayExist: true,
    });
    expect(harness.controller.getSnapshot().error?.message).not.toContain(
      "password",
    );
    stop();
    harness.dispose();
  });

  it.each([
    ["network failure", new ApiError("network")],
    ["timeout", new ApiError("timeout")],
    ["server failure", new ApiError("http", { status: 500 })],
    ["invalid response", new ApiError("invalid-response")],
  ])(
    "keeps an uncertain %s result manual until an explicit identity check",
    async (_case, error) => {
      const harness = makeHarness();
      const stop = await startPasswordRequired(harness);
      const ticket = await harness.controller.beginCredentialAction("password");
      await harness.controller.failPasswordChange(ticket!, error);

      expect(harness.controller.getSnapshot().state).toMatchObject({
        kind: "unavailable",
        reason: "password-unknown",
      });
      harness.focus();
      harness.externalSessionChange();
      expect(harness.currentUser).toHaveBeenCalledTimes(1);

      harness.currentUser.mockResolvedValueOnce(
        user({ passwordChangeRequired: true }),
      );
      await harness.controller.revalidate("manual");
      expect(harness.controller.getSnapshot().state.kind).toBe(
        "password-required",
      );
      expect(harness.currentUser).toHaveBeenCalledTimes(2);
      stop();
      harness.dispose();
    },
  );

  it("keeps uncertain login covered until a manual identity check", async () => {
    const harness = makeHarness();
    const stop = await startAnonymous(harness);
    const ticket = await harness.controller.beginCredentialAction("login");
    expect(ticket).not.toBeNull();
    await harness.controller.failLogin(ticket!, new ApiError("timeout"));
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "unavailable",
      reason: "after-login",
      sessionMayExist: true,
    });
    const callsAfterFailure = harness.currentUser.mock.calls.length;
    harness.focus();
    harness.externalSessionChange();
    expect(harness.currentUser).toHaveBeenCalledTimes(callsAfterFailure);
    await harness.controller.revalidate("manual");
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    stop();
    harness.dispose();
  });

  it("clears the private identity when a later check finds an expired session", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    harness.currentUser.mockRejectedValueOnce(
      httpError(401, "AUTHENTICATION_REQUIRED"),
    );

    await harness.controller.revalidate("manual");

    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      notice: "session-ended",
      privacyCovered: false,
    });
    expect(harness.queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it("clears private data before logout and confirms an HTTP 204", async () => {
    const harness = makeHarness();
    let resetsBeforePost = 0;
    harness.logoutRequest.mockImplementation(async () => {
      resetsBeforePost = harness.reset.mock.calls.length;
      return undefined;
    });
    const stop = await start(harness.controller);
    await harness.controller.logout();
    expect(resetsBeforePost).toBeGreaterThanOrEqual(1);
    expect(harness.controller.getSnapshot().state.kind).toBe("anonymous");
    expect(harness.controller.getSnapshot().notice).toBe("logout-complete");
    stop();
    harness.dispose();
  });

  it("drops focus revalidation deferred while logout is pending", async () => {
    const harness = makeHarness();
    let confirmLogout!: () => void;
    harness.logoutRequest.mockReturnValueOnce(
      new Promise<undefined>((resolve) => {
        confirmLogout = () => resolve(undefined);
      }),
    );
    const stop = await start(harness.controller);
    const logoutPromise = harness.controller.logout();
    await vi.waitFor(() =>
      expect(harness.controller.getSnapshot().pendingAction).toBe("logout"),
    );
    const readsBeforeLogoutResult = harness.currentUser.mock.calls.length;
    await harness.controller.revalidate("focus");
    confirmLogout();
    await logoutPromise;

    const ticket = await harness.controller.beginCredentialAction("login");
    expect(ticket).not.toBeNull();
    await harness.controller.sendLogin(ticket!, {
      identifier: "synthetic-user",
      password: "synthetic-password",
    });
    await harness.controller.completeLogin(ticket!);
    await Promise.resolve();

    expect(harness.currentUser).toHaveBeenCalledTimes(
      readsBeforeLogoutResult + 1,
    );
    stop();
    harness.dispose();
  });

  it("does not restore an uncertain logout on focus or external events", async () => {
    const harness = makeHarness();
    harness.logoutRequest.mockRejectedValue(new ApiError("timeout"));
    const stop = await start(harness.controller);
    await harness.controller.logout();
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "unavailable",
      reason: "logout-unknown",
    });
    const reads = harness.currentUser.mock.calls.length;
    harness.focus();
    harness.externalSessionChange();
    expect(harness.currentUser).toHaveBeenCalledTimes(reads);
    harness.currentUser.mockResolvedValueOnce(user());
    await harness.controller.revalidate("manual");
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    stop();
    harness.dispose();
  });

  it("makes a CSRF-rejected logout explicitly retryable without automatic replay", async () => {
    const harness = makeHarness();
    harness.logoutRequest
      .mockRejectedValueOnce(httpError(403, "CSRF_TOKEN_INVALID"))
      .mockResolvedValueOnce(undefined);
    const stop = await start(harness.controller);
    await harness.controller.logout();
    expect(harness.logoutRequest).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "restricted",
      reason: "forbidden",
    });
    await harness.controller.logout();
    expect(harness.logoutRequest).toHaveBeenCalledTimes(2);
    expect(harness.controller.getSnapshot().state.kind).toBe("anonymous");
    stop();
    harness.dispose();
  });

  it("treats an already inactive logout as anonymous without retrying it", async () => {
    const harness = makeHarness();
    harness.logoutRequest.mockRejectedValueOnce(
      httpError(401, "AUTHENTICATION_REQUIRED"),
    );
    const stop = await start(harness.controller);

    await harness.controller.logout();

    expect(harness.logoutRequest).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      notice: "logout-already-ended",
    });
    stop();
    harness.dispose();
  });

  it("applies focus throttling and always revalidates after returning visible", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    harness.focus();
    await harness.controller.revalidate("manual");
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    harness.setTime(12_000);
    harness.focus();
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    harness.setTime(15_000);
    harness.focus();
    await harness.controller.revalidate("manual");
    expect(harness.currentUser).toHaveBeenCalledTimes(3);

    harness.setVisible(false);
    expect(harness.controller.getSnapshot().privacyCovered).toBe(true);
    harness.setVisible(true);
    await harness.controller.revalidate("manual");
    expect(harness.currentUser).toHaveBeenCalledTimes(4);
    expect(harness.controller.getSnapshot().privacyCovered).toBe(false);
    stop();
    harness.dispose();
  });

  it("ignores a late identity response after returning from a hidden tab", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    let resolveLateRead!: (identity: CurrentUser) => void;
    const lateRead = new Promise<CurrentUser>((resolve) => {
      resolveLateRead = resolve;
    });
    harness.currentUser.mockReturnValueOnce(lateRead);

    const staleRead = harness.controller.revalidate("manual");
    await vi.waitFor(() =>
      expect(harness.currentUser).toHaveBeenCalledTimes(2),
    );
    harness.setVisible(false);
    harness.setVisible(true);
    await harness.controller.revalidate("manual");
    resolveLateRead(
      user({
        id: "00000000-0000-4000-8000-000000000099",
        username: "synthetic-stale-account",
      }),
    );
    await staleRead;

    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: identityFixture.id },
    });
    stop();
    harness.dispose();
  });

  it("revalidates a confirmed session after a cross-tab event without echoing it", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    harness.currentUser.mockResolvedValueOnce(
      user({ username: "synthetic-updated-account" }),
    );

    harness.externalSessionChange();
    await harness.controller.revalidate("manual");

    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { username: "synthetic-updated-account" },
    });
    expect(harness.sessionEvents.publish).not.toHaveBeenCalled();
    stop();
    harness.dispose();
  });

  it("restarts its listeners and session read cleanly after effect cleanup", async () => {
    const harness = makeHarness();
    const stopFirst = harness.controller.start();
    await harness.controller.revalidate("initial");
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    stopFirst();

    const stopSecond = harness.controller.start();
    await harness.controller.revalidate("initial");
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    expect(harness.controller.getSnapshot().state.kind).toBe("authenticated");
    stopSecond();
    harness.dispose();
  });

  it("clears cached private identity before publishing a different account", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    const accountB = user({
      id: "00000000-0000-4000-8000-000000000099",
      username: "synthetic-account-b",
    });
    harness.currentUser.mockResolvedValueOnce(accountB);
    await harness.controller.revalidate("manual");
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: accountB.id },
    });
    expect(harness.reset).toHaveBeenCalled();
    expect(harness.queryClient.getQueryCache().getAll()).toHaveLength(0);
    stop();
    harness.dispose();
  });

  it("issues one coordinated public identity ticket only for anonymous sessions", async () => {
    const harness = makeHarness();
    const stop = await startAnonymous(harness);

    const ticket =
      await harness.controller.beginPublicIdentityAction("invitation-inspect");
    expect(ticket).toMatchObject({
      kind: "public-identity",
      publicAction: "invitation-inspect",
    });
    expect(harness.controller.getSnapshot().pendingAction).toBe(
      "public-identity",
    );
    await expect(
      harness.controller.beginPublicIdentityAction("invitation-accept"),
    ).resolves.toBeNull();

    harness.controller.finishPublicIdentityAction(ticket!);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      pendingAction: null,
    });
    stop();
    harness.dispose();
  });

  it("defers focus and cross-tab checks while a public action is in flight", async () => {
    const harness = makeHarness();
    const stop = await startAnonymous(harness);
    const ticket =
      await harness.controller.beginPublicIdentityAction("recovery-complete");
    harness.currentUser.mockResolvedValueOnce(user());

    harness.focus();
    harness.externalSessionChange();
    expect(ticket?.signal.aborted).toBe(false);
    expect(harness.currentUser).toHaveBeenCalledTimes(1);

    harness.controller.finishPublicIdentityAction(ticket!);
    await vi.waitFor(() =>
      expect(harness.controller.getSnapshot().state.kind).toBe("authenticated"),
    );
    expect(harness.currentUser).toHaveBeenCalledTimes(2);

    stop();
    harness.dispose();
  });

  it.each(["authenticated", "password-required"] as const)(
    "does not issue a public identity ticket for a %s session",
    async (sessionKind) => {
      const harness = makeHarness();
      if (sessionKind === "password-required") {
        harness.currentUser.mockResolvedValueOnce(
          user({ passwordChangeRequired: true }),
        );
      }
      const stop = await start(harness.controller);

      await expect(
        harness.controller.beginPublicIdentityAction("recovery-complete"),
      ).resolves.toBeNull();
      expect(harness.controller.getSnapshot().pendingAction).toBeNull();

      stop();
      harness.dispose();
    },
  );
});
