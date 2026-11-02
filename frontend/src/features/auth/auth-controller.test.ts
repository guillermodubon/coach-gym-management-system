import { describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../../shared/api/client";
import type { BranchContext, CurrentUser } from "../../shared/api/contracts";
import { ApiError } from "../../shared/api/errors";
import { createQueryClient } from "../../shared/api/query-client";
import { createContextChangeGuard } from "../branch-context/context-change-guard";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { createAuthController } from "./auth-controller";
import type { AuthController } from "./auth-controller";
import { validateAuthority } from "./auth-model";
import type { SessionEvents, VisibilityEvents } from "./session-events";

type IdentityApi = ReturnType<
  typeof import("../../shared/api/identity").createIdentityApi
>;

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
  const contextChangeGuard = createContextChangeGuard();
  const branchContext: BranchContext = {
    organizationId: branchFixture.organizationId,
    scopeType: "ORGANIZATION" as const,
    activeBranch: branchFixture,
    availableBranches: [branchFixture],
  };
  const selectBranchContext = vi.fn<IdentityApi["selectBranchContext"]>(
    async () => branchContext,
  );
  const clearBranchContext = vi.fn<IdentityApi["clearBranchContext"]>(
    async () => undefined,
  );
  const identityApi = {
    currentUser,
    login,
    logout: logoutRequest,
    branchContext: vi.fn(async () => branchContext),
    selectBranchContext,
    clearBranchContext,
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
    contextChangeGuard,
  });

  return {
    controller,
    contextChangeGuard,
    apiClient,
    getCsrf,
    currentUser,
    identityApi,
    selectBranchContext,
    clearBranchContext,
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

  it("keeps a confirmed visible session mounted while focus revalidation is pending", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    let resolveRead!: (identity: CurrentUser) => void;
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveRead = resolve;
      }),
    );

    const check = harness.controller.revalidate("focus");
    await vi.waitFor(() =>
      expect(harness.currentUser).toHaveBeenCalledTimes(2),
    );
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "authenticated", user: { id: identityFixture.id } },
      privacyCovered: false,
    });

    resolveRead(user());
    await check;
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "authenticated", user: { id: identityFixture.id } },
      privacyCovered: false,
    });
    stop();
    harness.dispose();
  });

  it.each([
    ["expired", httpError(401, "AUTHENTICATION_REQUIRED"), "anonymous"],
    ["forbidden", httpError(403, "ACCESS_DENIED"), "restricted"],
    ["unreachable", new ApiError("network"), "unavailable"],
  ] as const)(
    "covers and clears an %s session after a focus read fails",
    async (_name, error, expectedState) => {
      const harness = makeHarness();
      const stop = await start(harness.controller);
      let rejectRead!: (error: ApiError) => void;
      harness.currentUser.mockReturnValueOnce(
        new Promise<CurrentUser>((_resolve, reject) => {
          rejectRead = reject;
        }),
      );
      harness.queryClient.setQueryData(["private", "synthetic-profile"], {
        marker: true,
      });
      harness.reset.mockImplementation(() => {
        expect(harness.controller.getSnapshot()).toMatchObject({
          state: { kind: "checking" },
          privacyCovered: true,
        });
      });

      const check = harness.controller.revalidate("focus");
      rejectRead(error);
      await check;
      expect(harness.reset).toHaveBeenCalled();
      expect(harness.controller.getSnapshot().state.kind).toBe(expectedState);
      expect(
        harness.queryClient.getQueryData(["private", "synthetic-profile"]),
      ).toBeUndefined();
      stop();
      harness.dispose();
    },
  );

  it("covers the old actor before clearing data when a focus read confirms another actor", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    let resolveRead!: (identity: CurrentUser) => void;
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveRead = resolve;
      }),
    );
    harness.reset.mockImplementation(() => {
      expect(harness.controller.getSnapshot()).toMatchObject({
        state: { kind: "checking" },
        privacyCovered: true,
      });
    });

    const check = harness.controller.revalidate("focus");
    resolveRead(
      user({
        id: "00000000-0000-4000-8000-000000000099",
        username: "synthetic-next-actor",
      }),
    );
    await check;
    expect(harness.reset).toHaveBeenCalled();
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: "00000000-0000-4000-8000-000000000099" },
    });
    stop();
    harness.dispose();
  });

  it("starts a fresh blocking manual check while focus is pending", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    let resolveRead!: (identity: CurrentUser) => void;
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveRead = resolve;
      }),
    );

    const focus = harness.controller.revalidate("focus");
    let resolveManual!: (identity: CurrentUser) => void;
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveManual = resolve;
      }),
    );
    const manual = harness.controller.revalidate("manual");
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "checking" },
      privacyCovered: true,
    });
    resolveManual(user());
    await manual;
    resolveRead(user({ id: "00000000-0000-4000-8000-000000000099" }));
    await Promise.all([focus, manual]);
    expect(harness.currentUser).toHaveBeenCalledTimes(3);
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: identityFixture.id },
    });
    stop();
    harness.dispose();
  });

  it("restarts a pending focus read after an external session change and ignores its stale response", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    let resolveStaleRead!: (identity: CurrentUser) => void;
    let resolveFreshRead!: (identity: CurrentUser) => void;
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveStaleRead = resolve;
      }),
    );
    const focus = harness.controller.revalidate("focus");
    harness.currentUser.mockReturnValueOnce(
      new Promise<CurrentUser>((resolve) => {
        resolveFreshRead = resolve;
      }),
    );

    harness.externalSessionChange();
    await vi.waitFor(() =>
      expect(harness.currentUser).toHaveBeenCalledTimes(3),
    );
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "checking" },
      privacyCovered: true,
    });
    const external = harness.controller.revalidate("manual");
    resolveFreshRead(user());
    await external;
    resolveStaleRead(user({ id: "00000000-0000-4000-8000-000000000099" }));
    await focus;
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { id: identityFixture.id },
    });
    stop();
    harness.dispose();
  });

  it("applies focus throttling and always revalidates after returning visible", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);
    const beforeFirstFocus = harness.controller.getSnapshot().revision;
    harness.focus();
    await harness.controller.revalidate("focus");
    await vi.waitFor(() =>
      expect(harness.controller.getSnapshot().revision).toBeGreaterThan(
        beforeFirstFocus,
      ),
    );
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    harness.setTime(12_000);
    harness.focus();
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    harness.setTime(15_001);
    await harness.controller.revalidate("focus");
    await vi.waitFor(() =>
      expect(harness.currentUser).toHaveBeenCalledTimes(3),
    );

    harness.setVisible(false);
    expect(harness.controller.getSnapshot().privacyCovered).toBe(true);
    harness.setVisible(true);
    await harness.controller.revalidate("manual");
    expect(harness.currentUser).toHaveBeenCalledTimes(4);
    expect(harness.controller.getSnapshot().privacyCovered).toBe(false);
    stop();
    harness.dispose();
  });

  it.each(["manual", "focus"] as const)(
    "ignores a late %s identity response after returning from a hidden tab",
    async (reason) => {
      const harness = makeHarness();
      const stop = await start(harness.controller);
      let resolveLateRead!: (identity: CurrentUser) => void;
      const lateRead = new Promise<CurrentUser>((resolve) => {
        resolveLateRead = resolve;
      });
      harness.currentUser.mockReturnValueOnce(lateRead);

      const staleRead = harness.controller.revalidate(reason);
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
    },
  );

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

  it("publishes a metadata-free session signal for an external status change", async () => {
    const harness = makeHarness();
    const stop = await start(harness.controller);

    harness.controller.notifySessionChange();

    expect(harness.sessionEvents.publish).toHaveBeenCalledTimes(1);
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

describe("auth controller branch context coordination", () => {
  const branchA = branchFixture;
  const branchB = {
    ...branchFixture,
    id: "00000000-0000-4000-8000-000000000004",
    code: "SECOND",
    name: "Second synthetic branch",
    initialBranch: false,
  };
  const branchC = {
    ...branchFixture,
    id: "00000000-0000-4000-8000-000000000005",
    code: "THIRD",
    name: "Third synthetic branch",
    initialBranch: false,
  };

  it("does not send the already active selection", async () => {
    const harness = makeHarness();
    harness.currentUser.mockResolvedValueOnce(user({ activeBranch: branchA }));
    const stop = await start(harness.controller);

    await harness.controller.selectBranchContext(branchA.id);

    expect(harness.selectBranchContext).not.toHaveBeenCalled();
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot().branchContextTransition).toEqual({
      status: "idle",
      notice: "context-unchanged",
      error: null,
    });
    stop();
    harness.dispose();
  });

  it("serializes one switch, cancels old private reads and accepts only fresh identity", async () => {
    const harness = makeHarness();
    const availableBranches = [branchA, branchB];
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchA, availableBranches }),
    );
    const stop = await start(harness.controller);
    const originalEpoch = harness.controller.getSnapshot().contextEpoch;
    let querySignal: AbortSignal | undefined;
    let finishStaleQuery: ((value: string) => void) | undefined;
    const staleRead = harness.queryClient
      .fetchQuery({
        queryKey: ["private", "synthetic-branch-read", branchA.id],
        queryFn: ({ signal }) => {
          querySignal = signal;
          return new Promise<string>((resolve) => {
            finishStaleQuery = resolve;
          });
        },
      })
      .catch(() => undefined);
    await vi.waitFor(() => expect(querySignal).toBeDefined());

    harness.selectBranchContext.mockResolvedValueOnce({
      organizationId: branchA.organizationId,
      scopeType: "BRANCH",
      activeBranch: branchB,
      availableBranches,
    });
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchB, availableBranches }),
    );

    const switchRequest = harness.controller.selectBranchContext(branchB.id);
    await vi.waitFor(() =>
      expect(harness.controller.getSnapshot().pendingAction).toBe(
        "branch-context",
      ),
    );
    await harness.controller.selectBranchContext(branchA.id);
    expect(querySignal?.aborted).toBe(true);
    finishStaleQuery?.("stale branch data");
    await staleRead;
    await vi.waitFor(() =>
      expect(harness.selectBranchContext).toHaveBeenCalledTimes(1),
    );
    await switchRequest;

    const snapshot = harness.controller.getSnapshot();
    expect(snapshot.state).toMatchObject({
      kind: "authenticated",
      user: { activeBranch: { id: branchB.id } },
    });
    expect(snapshot.contextEpoch).toBe(originalEpoch + 1);
    expect(snapshot.branchContextTransition).toMatchObject({
      status: "idle",
      notice: "context-confirmed",
    });
    expect(
      harness.queryClient.getQueryData([
        "private",
        "synthetic-branch-read",
        branchA.id,
      ]),
    ).toBeUndefined();
    stop();
    harness.dispose();
  });

  it("keeps the server-effective branch when another selection wins the race", async () => {
    const harness = makeHarness();
    const availableBranches = [branchA, branchB, branchC];
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchA, availableBranches }),
    );
    const stop = await start(harness.controller);
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchC, availableBranches }),
    );

    await harness.controller.selectBranchContext(branchB.id);

    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { activeBranch: { id: branchC.id } },
    });
    expect(
      harness.controller.getSnapshot().branchContextTransition,
    ).toMatchObject({
      status: "idle",
      notice: "effective-context-differs",
    });
    stop();
    harness.dispose();
  });

  it("accepts a different actor only from the fresh reconciliation and advances the context epoch", async () => {
    const harness = makeHarness();
    const availableBranches = [branchA, branchB];
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchA, availableBranches }),
    );
    const stop = await start(harness.controller);
    const originalEpoch = harness.controller.getSnapshot().contextEpoch;
    const nextActor = user({
      id: "00000000-0000-4000-8000-000000000099",
      username: "synthetic-next-actor",
      activeBranch: branchB,
      availableBranches,
    });
    harness.currentUser.mockResolvedValueOnce(nextActor);

    await harness.controller.selectBranchContext(branchB.id);

    expect(harness.controller.getSnapshot()).toMatchObject({
      state: {
        kind: "authenticated",
        user: {
          id: nextActor.id,
          username: nextActor.username,
          activeBranch: { id: branchB.id },
        },
      },
      contextEpoch: originalEpoch + 1,
      branchContextTransition: {
        status: "idle",
        notice: "context-reconciled",
      },
    });
    expect(harness.selectBranchContext).toHaveBeenCalledTimes(1);
    stop();
    harness.dispose();
  });

  it("clears a one-branch preference without assuming the effective branch becomes null", async () => {
    const harness = makeHarness();
    harness.currentUser.mockResolvedValueOnce(user({ activeBranch: branchA }));
    const stop = await start(harness.controller);
    harness.currentUser.mockResolvedValueOnce(user({ activeBranch: branchA }));

    await harness.controller.clearBranchContext();

    expect(harness.identityApi.clearBranchContext).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot().state).toMatchObject({
      kind: "authenticated",
      user: { activeBranch: { id: branchA.id } },
    });
    expect(
      harness.controller.getSnapshot().branchContextTransition,
    ).toMatchObject({
      status: "idle",
      notice: "context-reconciled",
    });
    stop();
    harness.dispose();
  });

  it("does not admit a transition while a profile owner reports a dirty draft", async () => {
    const harness = makeHarness();
    harness.currentUser.mockResolvedValueOnce(
      user({ availableBranches: [branchA, branchB] }),
    );
    const stop = await start(harness.controller);
    const unregister = harness.contextChangeGuard.registerBlocker(() => true);

    await harness.controller.selectBranchContext(branchB.id);

    expect(harness.selectBranchContext).not.toHaveBeenCalled();
    expect(harness.controller.getSnapshot().branchContextTransition).toEqual({
      status: "blocked",
      notice: null,
      error: null,
      reason: "unsaved-changes",
    });
    unregister();
    stop();
    harness.dispose();
  });

  it("keeps uncertain writes covered until a manual read and never replays the mutation", async () => {
    const harness = makeHarness();
    const availableBranches = [branchA, branchB];
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchA, availableBranches }),
    );
    const stop = await start(harness.controller);
    harness.selectBranchContext.mockRejectedValueOnce(new ApiError("timeout"));

    await harness.controller.selectBranchContext(branchB.id);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "unavailable", reason: "branch-context-unknown" },
      branchContextTransition: { status: "uncertain" },
    });
    expect(harness.currentUser).toHaveBeenCalledTimes(1);

    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchB, availableBranches }),
    );
    await harness.controller.reconcileBranchContext();

    expect(harness.selectBranchContext).toHaveBeenCalledTimes(1);
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: {
        kind: "authenticated",
        user: { activeBranch: { id: branchB.id } },
      },
      branchContextTransition: {
        status: "idle",
        notice: "context-reconciled",
      },
    });
    stop();
    harness.dispose();
  });

  it("recovers an uncertain context on focus when no cross-tab event arrives", async () => {
    const harness = makeHarness();
    const availableBranches = [branchA, branchB];
    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchA, availableBranches }),
    );
    const stop = await start(harness.controller);
    harness.selectBranchContext.mockRejectedValueOnce(new ApiError("timeout"));

    await harness.controller.selectBranchContext(branchB.id);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "unavailable", reason: "branch-context-unknown" },
      branchContextTransition: { status: "uncertain" },
    });

    harness.currentUser.mockResolvedValueOnce(
      user({ activeBranch: branchB, availableBranches }),
    );
    harness.focus();

    await vi.waitFor(() =>
      expect(harness.controller.getSnapshot()).toMatchObject({
        state: {
          kind: "authenticated",
          user: { activeBranch: { id: branchB.id } },
        },
        branchContextTransition: {
          status: "idle",
          notice: "context-reconciled",
        },
      }),
    );
    expect(harness.selectBranchContext).toHaveBeenCalledTimes(1);
    expect(harness.currentUser).toHaveBeenCalledTimes(2);
    stop();
    harness.dispose();
  });

  it("reconciles definitive CSRF and conflict failures without replaying the mutation", async () => {
    const cases = [
      new ApiError("http", { status: 403, code: "CSRF_TOKEN_INVALID" }),
      new ApiError("http", { status: 409, code: "ACTIVE_BRANCH_UNAVAILABLE" }),
    ];

    for (const failure of cases) {
      const harness = makeHarness();
      const availableBranches = [branchA, branchB];
      harness.currentUser.mockResolvedValueOnce(
        user({ activeBranch: branchA, availableBranches }),
      );
      const stop = await start(harness.controller);
      harness.selectBranchContext.mockRejectedValueOnce(failure);
      harness.currentUser.mockResolvedValueOnce(
        user({ activeBranch: branchA, availableBranches }),
      );

      await harness.controller.selectBranchContext(branchB.id);

      expect(harness.selectBranchContext).toHaveBeenCalledTimes(1);
      expect(harness.currentUser).toHaveBeenCalledTimes(2);
      expect(harness.controller.getSnapshot()).toMatchObject({
        state: { kind: "authenticated" },
        branchContextTransition: { status: "failed", error: failure },
      });
      stop();
      harness.dispose();
    }
  });

  it("lets logout supersede a pending context request and ignores its late success", async () => {
    const harness = makeHarness();
    harness.currentUser.mockResolvedValueOnce(
      user({ availableBranches: [branchA, branchB] }),
    );
    const stop = await start(harness.controller);
    let finishSelection: (() => void) | undefined;
    harness.selectBranchContext.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishSelection = () => resolve(harness.identityApi.branchContext());
        }),
    );

    const switchRequest = harness.controller.selectBranchContext(branchB.id);
    await vi.waitFor(() =>
      expect(harness.selectBranchContext).toHaveBeenCalledTimes(1),
    );
    await harness.controller.logout();
    finishSelection?.();
    await switchRequest;

    expect(harness.logoutRequest).toHaveBeenCalledTimes(1);
    expect(harness.currentUser).toHaveBeenCalledTimes(1);
    expect(harness.controller.getSnapshot()).toMatchObject({
      state: { kind: "anonymous" },
      pendingAction: null,
      branchContextTransition: { status: "idle" },
    });
    stop();
    harness.dispose();
  });
});
