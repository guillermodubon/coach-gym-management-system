import type { QueryClient } from "@tanstack/react-query";
import type { ApiClient } from "../../shared/api/client";
import { clearPrivateData } from "../../shared/api/query-client";
import type { ApiError } from "../../shared/api/errors";
import { createIdentityApi } from "../../shared/api/identity";
import type {
  AuthorizedBranch,
  LoginRequest,
  PasswordChangeResult,
} from "../../shared/api/contracts";
import { validateAuthority } from "./auth-model";
import type { AuthSnapshot, AuthState, MutationTicket } from "./auth-model";
import type { SessionEvents, VisibilityEvents } from "./session-events";

type IdentityApi = ReturnType<typeof createIdentityApi>;
type RevalidationReason =
  "initial" | "focus" | "visible" | "manual" | "external";

export interface AuthControllerDependencies {
  apiClient: ApiClient;
  identityApi: IdentityApi;
  queryClient: QueryClient;
  now: () => number;
  visibilityEvents: VisibilityEvents;
  sessionEvents: SessionEvents;
}

export interface AuthController {
  subscribe(listener: () => void): () => void;
  getSnapshot(): AuthSnapshot;
  start(): () => void;
  revalidate(reason: RevalidationReason): Promise<void>;
  beginCredentialAction(
    kind: "login" | "password",
  ): Promise<MutationTicket | null>;
  beginPublicIdentityAction(
    kind:
      | "recovery-request"
      | "recovery-complete"
      | "invitation-inspect"
      | "invitation-accept",
  ): Promise<MutationTicket | null>;
  finishPublicIdentityAction(ticket: MutationTicket): void;
  isTicketCurrent(ticket: MutationTicket): boolean;
  completeLogin(ticket: MutationTicket): Promise<void>;
  failLogin(ticket: MutationTicket, error: ApiError): Promise<void>;
  sendLogin(ticket: MutationTicket, credentials: LoginRequest): Promise<void>;
  completePasswordChange(
    ticket: MutationTicket,
    result: PasswordChangeResult,
  ): Promise<void>;
  failPasswordChange(ticket: MutationTicket, error: ApiError): Promise<void>;
  logout(): Promise<void>;
}

const identityKey = ["private", "identity"] as const;
const focusThrottleMs = 5_000;

function isHttpError(error: unknown, status: number, code?: string): boolean {
  return (
    error instanceof Error &&
    "status" in error &&
    error.status === status &&
    (code === undefined || ("code" in error && error.code === code))
  );
}

function safeApiError(error: unknown): ApiError | null {
  return error instanceof Error && "kind" in error && "status" in error
    ? (error as ApiError)
    : null;
}

function isCancelled(error: unknown): boolean {
  return (
    error instanceof Error && "kind" in error && error.kind === "cancelled"
  );
}

function combineSignals(first: AbortSignal, second?: AbortSignal): AbortSignal {
  return second ? AbortSignal.any([first, second]) : first;
}

function stateUser(state: AuthState) {
  return state.kind === "authenticated" || state.kind === "password-required"
    ? state.user
    : null;
}

function hasPrivateState(state: AuthState): boolean {
  return state.kind === "authenticated" || state.kind === "password-required";
}

function sameBranch(a: AuthorizedBranch, b: AuthorizedBranch) {
  return (
    a.id === b.id &&
    a.organizationId === b.organizationId &&
    a.code === b.code &&
    a.name === b.name &&
    a.timezone === b.timezone &&
    a.initialBranch === b.initialBranch
  );
}

function sameIdentity(
  previous: ReturnType<typeof stateUser>,
  next: Parameters<typeof validateAuthority>[0],
): boolean {
  if (!previous || previous.id !== next.id) return false;
  if (
    previous.username !== next.username ||
    previous.fullName !== next.fullName ||
    previous.organizationScope !== next.organizationScope ||
    previous.passwordChangeRequired !== next.passwordChangeRequired ||
    previous.roles.length !== next.roles.length ||
    previous.roles.some((role, index) => role !== next.roles[index]) ||
    previous.availableBranches.length !== next.availableBranches.length
  ) {
    return false;
  }

  if (
    Boolean(previous.activeBranch) !== Boolean(next.activeBranch) ||
    (previous.activeBranch &&
      next.activeBranch &&
      !sameBranch(previous.activeBranch, next.activeBranch))
  ) {
    return false;
  }

  return previous.availableBranches.every((branch, index) => {
    const other = next.availableBranches[index];
    return other !== undefined && sameBranch(branch, other);
  });
}

function immutableUser<T extends Parameters<typeof validateAuthority>[0]>(
  user: T,
): T {
  return Object.freeze({
    ...user,
    roles: Object.freeze([...user.roles]),
    availableBranches: Object.freeze(
      user.availableBranches.map((branch) => Object.freeze({ ...branch })),
    ),
    activeBranch: user.activeBranch
      ? Object.freeze({ ...user.activeBranch })
      : null,
  }) as T;
}

export function createAuthController({
  apiClient,
  identityApi,
  queryClient,
  now,
  visibilityEvents,
  sessionEvents,
}: AuthControllerDependencies): AuthController {
  const listeners = new Set<() => void>();
  let snapshot: AuthSnapshot = Object.freeze<AuthSnapshot>({
    state: { kind: "checking" },
    pendingAction: null,
    error: null,
    notice: null,
    privacyCovered: true,
    revision: 0,
  });
  let mounted = false;
  let readGeneration = 0;
  let queryEpoch = 0;
  let readAbort: AbortController | undefined;
  let readPromise: Promise<void> | undefined;
  let ticketSequence = 0;
  let activeTicket: MutationTicket | undefined;
  let logoutSequence = 0;
  let activeLogout: number | undefined;
  let unsubscribeRuntime: (() => void) | undefined;
  let deferredRevalidation = false;
  let needsVisibleRevalidation = false;
  let hidden = !visibilityEvents.isVisible();
  let lastFocusAt = Number.NEGATIVE_INFINITY;
  let hasConfirmedSession = false;
  let validatedUser: Awaited<
    ReturnType<typeof identityApi.currentUser>
  > | null = null;
  let manualOnlyRevalidation = false;
  let loginRetryAt = 0;
  let logoutRetryAt = 0;
  let passwordRetryAt = 0;

  function publish(updates: Partial<Omit<AuthSnapshot, "revision">>): void {
    snapshot = Object.freeze({
      ...snapshot,
      ...updates,
      revision: snapshot.revision + 1,
    });
    for (const listener of listeners) listener();
  }

  function invalidateRead(): void {
    readGeneration++;
    readAbort?.abort();
    readAbort = undefined;
    readPromise = undefined;
    void queryClient.cancelQueries({ queryKey: identityKey });
  }

  function generationIsCurrent(generation: number): boolean {
    return mounted && readGeneration === generation;
  }

  async function fetchCurrentUser(signal: AbortSignal) {
    const queryEpochForRequest = ++queryEpoch;
    return queryClient.fetchQuery({
      queryKey: [...identityKey, queryEpochForRequest],
      queryFn: ({ signal: querySignal }) =>
        identityApi.currentUser({
          signal: combineSignals(signal, querySignal),
        }),
      retry: false,
      gcTime: 0,
      staleTime: 0,
    });
  }

  async function fetchUserWithBranchRetry(signal: AbortSignal) {
    try {
      return await fetchCurrentUser(signal);
    } catch (error) {
      if (!isHttpError(error, 409, "ACTIVE_BRANCH_UNAVAILABLE")) throw error;
      return fetchCurrentUser(signal);
    }
  }

  function finishDeferredRevalidation(): void {
    if (!deferredRevalidation || snapshot.pendingAction !== null) return;
    deferredRevalidation = false;
    queueMicrotask(() => void revalidate("external"));
  }

  async function clearAndPublishRestricted(
    reason:
      | "unsupported-authority"
      | "branch-context"
      | "forbidden"
      | "inconsistent-context",
    error: ApiError | null,
    generation?: number,
    broadcast = false,
  ): Promise<void> {
    await clearPrivateData(queryClient, apiClient);
    if (generation !== undefined && !generationIsCurrent(generation)) return;
    hasConfirmedSession = false;
    validatedUser = null;
    manualOnlyRevalidation = reason === "forbidden";
    publish({
      state: { kind: "restricted", reason, sessionMayExist: true },
      pendingAction: null,
      error,
      notice: null,
      privacyCovered: false,
    });
    if (broadcast) sessionEvents.publish();
  }

  async function handleReadError(
    error: unknown,
    generation: number,
    reason: RevalidationReason | "after-login",
  ): Promise<void> {
    if (!generationIsCurrent(generation) || isCancelled(error)) return;
    const apiError = safeApiError(error);

    if (apiError?.clientReason === "unsupported-authority") {
      await clearAndPublishRestricted(
        "unsupported-authority",
        apiError,
        generation,
        reason !== "external",
      );
      return;
    }
    if (isHttpError(error, 401)) {
      const hadSession = hasConfirmedSession;
      await clearPrivateData(queryClient, apiClient);
      if (!generationIsCurrent(generation)) return;
      hasConfirmedSession = false;
      validatedUser = null;
      manualOnlyRevalidation = false;
      publish({
        state: { kind: "anonymous" },
        pendingAction: null,
        error: null,
        notice:
          reason === "after-login"
            ? "login-not-established"
            : hadSession
              ? "session-ended"
              : null,
        privacyCovered: false,
      });
      if (hadSession && reason !== "external") sessionEvents.publish();
      return;
    }
    if (isHttpError(error, 403)) {
      await clearAndPublishRestricted(
        "forbidden",
        apiError,
        generation,
        reason !== "external",
      );
      return;
    }
    if (isHttpError(error, 409, "ACTIVE_BRANCH_UNAVAILABLE")) {
      await clearAndPublishRestricted(
        "branch-context",
        apiError,
        generation,
        reason !== "external",
      );
      return;
    }

    await clearPrivateData(queryClient, apiClient);
    if (!generationIsCurrent(generation)) return;
    hasConfirmedSession = false;
    validatedUser = null;
    manualOnlyRevalidation = reason === "after-login";
    publish({
      state: {
        kind: "unavailable",
        reason:
          reason === "after-login"
            ? "after-login"
            : apiError?.kind === "invalid-response"
              ? "invalid-response"
              : "connection",
        sessionMayExist: true,
      },
      pendingAction: null,
      error: apiError,
      notice: reason === "after-login" ? "login-uncertain" : null,
      privacyCovered: false,
    });
    if (reason === "after-login") {
      sessionEvents.publish();
    }
  }

  async function acceptUser(
    user: Awaited<ReturnType<typeof identityApi.currentUser>>,
    generation: number,
    reason: RevalidationReason | "after-login",
  ): Promise<void> {
    if (!generationIsCurrent(generation)) return;
    const validation = validateAuthority(user);
    if (!validation.ok) {
      await clearAndPublishRestricted(validation.reason, null, generation);
      if (reason === "after-login") sessionEvents.publish();
      return;
    }

    const previousUser = validatedUser ?? stateUser(snapshot.state);
    if (previousUser && !sameIdentity(previousUser, user)) {
      await clearPrivateData(queryClient, apiClient);
      if (!generationIsCurrent(generation)) return;
    }

    const immutable = immutableUser(user);
    hasConfirmedSession = true;
    validatedUser = immutable;
    manualOnlyRevalidation = false;
    publish({
      state: immutable.passwordChangeRequired
        ? { kind: "password-required", user: immutable }
        : { kind: "authenticated", user: immutable },
      pendingAction: null,
      error: null,
      notice: null,
      privacyCovered: hidden,
    });
    if (reason === "after-login") sessionEvents.publish();
  }

  async function runRead(
    generation: number,
    signal: AbortSignal,
    reason: RevalidationReason | "after-login",
  ): Promise<void> {
    try {
      const user = await fetchUserWithBranchRetry(signal);
      await acceptUser(user, generation, reason);
    } catch (error) {
      await handleReadError(error, generation, reason);
    }
  }

  function revalidate(reason: RevalidationReason): Promise<void> {
    if (
      snapshot.pendingAction !== null ||
      (manualOnlyRevalidation && reason !== "manual") ||
      (snapshot.state.kind === "unavailable" &&
        snapshot.state.reason === "logout-unknown" &&
        reason !== "manual")
    ) {
      deferredRevalidation = true;
      return Promise.resolve();
    }
    if (reason === "focus") {
      const currentTime = now();
      if (currentTime - lastFocusAt < focusThrottleMs) return Promise.resolve();
      lastFocusAt = currentTime;
    }
    if (readPromise) return readPromise;

    const generation = ++readGeneration;
    const controller = new AbortController();
    readAbort = controller;
    publish({
      state: { kind: "checking" },
      privacyCovered: true,
      error: null,
    });
    const promise = runRead(generation, controller.signal, reason).finally(
      () => {
        if (readGeneration === generation) {
          readAbort = undefined;
          readPromise = undefined;
        }
      },
    );
    readPromise = promise;
    return promise;
  }

  async function beginCredentialAction(
    kind: "login" | "password",
  ): Promise<MutationTicket | null> {
    const currentUser = stateUser(snapshot.state);
    if (
      snapshot.pendingAction !== null ||
      (kind === "login" && snapshot.state.kind !== "anonymous") ||
      (kind === "password" &&
        snapshot.state.kind !== "password-required" &&
        snapshot.state.kind !== "authenticated") ||
      (kind === "login" && now() < loginRetryAt) ||
      (kind === "password" && now() < passwordRetryAt)
    ) {
      return null;
    }
    const expectedUserId = kind === "password" ? currentUser?.id : undefined;
    const passwordMode =
      kind === "password"
        ? snapshot.state.kind === "password-required"
          ? "required"
          : "voluntary"
        : undefined;
    if (kind === "password" && (!expectedUserId || !passwordMode)) return null;
    invalidateRead();
    const ticket = Object.freeze({
      id: ++ticketSequence,
      kind,
      signal: new AbortController().signal,
      ...(expectedUserId ? { expectedUserId } : {}),
      ...(passwordMode ? { passwordMode } : {}),
    });
    activeTicket = ticket;
    if (kind === "login") validatedUser = null;
    publish({
      pendingAction: kind,
      error: null,
      notice: null,
      privacyCovered: true,
    });
    await clearPrivateData(queryClient, apiClient);
    return isTicketCurrent(ticket) ? ticket : null;
  }

  async function beginPublicIdentityAction(
    kind: "recovery-request" | "recovery-complete",
  ): Promise<MutationTicket | null> {
    if (
      snapshot.pendingAction !== null ||
      snapshot.state.kind !== "anonymous"
    ) {
      return null;
    }

    invalidateRead();
    const ticket: MutationTicket = Object.freeze({
      id: ++ticketSequence,
      kind: "public-identity",
      publicAction: kind,
      signal: new AbortController().signal,
    });
    activeTicket = ticket;
    publish({
      pendingAction: "public-identity",
      error: null,
      notice: null,
      privacyCovered: false,
    });
    await clearPrivateData(queryClient, apiClient);
    return isTicketCurrent(ticket) ? ticket : null;
  }

  function finishPublicIdentityAction(ticket: MutationTicket): void {
    if (ticket.kind !== "public-identity" || !isTicketCurrent(ticket)) {
      return;
    }
    activeTicket = undefined;
    publish({
      pendingAction: null,
      error: null,
      notice: null,
      privacyCovered: false,
    });
    finishDeferredRevalidation();
  }

  function isTicketCurrent(ticket: MutationTicket): boolean {
    return (
      activeTicket?.id === ticket.id &&
      activeTicket.kind === ticket.kind &&
      snapshot.pendingAction === ticket.kind
    );
  }

  async function sendLogin(
    ticket: MutationTicket,
    credentials: LoginRequest,
  ): Promise<void> {
    if (ticket.kind !== "login" || !isTicketCurrent(ticket)) return;
    await identityApi.login(credentials, { signal: ticket.signal });
  }

  async function completeLogin(ticket: MutationTicket): Promise<void> {
    if (ticket.kind !== "login" || !isTicketCurrent(ticket)) return;
    publish({ state: { kind: "checking" }, privacyCovered: true, error: null });
    try {
      await apiClient.getCsrf(ticket.signal);
      if (!isTicketCurrent(ticket)) return;
      const generation = ++readGeneration;
      const controller = new AbortController();
      readAbort = controller;
      const signal = combineSignals(ticket.signal, controller.signal);
      try {
        const user = await fetchUserWithBranchRetry(signal);
        if (!isTicketCurrent(ticket)) return;
        activeTicket = undefined;
        readAbort = undefined;
        await acceptUser(user, generation, "after-login");
      } catch (error) {
        if (!isTicketCurrent(ticket)) return;
        activeTicket = undefined;
        readAbort = undefined;
        await handleReadError(error, generation, "after-login");
      }
    } catch (error) {
      if (!isTicketCurrent(ticket)) return;
      activeTicket = undefined;
      await handleReadError(error, readGeneration, "after-login");
    } finally {
      if (activeTicket?.id === ticket.id) activeTicket = undefined;
      finishDeferredRevalidation();
    }
  }

  async function failLogin(
    ticket: MutationTicket,
    error: ApiError,
  ): Promise<void> {
    if (ticket.kind !== "login" || !isTicketCurrent(ticket)) return;
    activeTicket = undefined;
    const knownFailure =
      error.status === 400 ||
      error.status === 401 ||
      error.code === "CSRF_TOKEN_INVALID" ||
      error.status === 429;
    if (!knownFailure) {
      await clearPrivateData(queryClient, apiClient);
      loginRetryAt = Math.max(
        loginRetryAt,
        now() + (error.retryAfterSeconds ?? 0) * 1000,
      );
      manualOnlyRevalidation = true;
      publish({
        state: {
          kind: "unavailable",
          reason: "after-login",
          sessionMayExist: true,
        },
        pendingAction: null,
        error,
        notice: "login-uncertain",
        privacyCovered: false,
      });
      sessionEvents.publish();
      finishDeferredRevalidation();
      return;
    }

    if (error.status === 429 && error.retryAfterSeconds !== undefined) {
      loginRetryAt = now() + error.retryAfterSeconds * 1000;
    }
    publish({
      state: { kind: "anonymous" },
      pendingAction: null,
      error,
      notice: null,
      privacyCovered: false,
    });
    finishDeferredRevalidation();
  }

  async function completePasswordChange(
    ticket: MutationTicket,
    result: PasswordChangeResult,
  ): Promise<void> {
    if (ticket.kind !== "password" || !isTicketCurrent(ticket)) return;

    if (
      !ticket.expectedUserId ||
      !passwordTicketMatchesCurrentActor(ticket) ||
      result.userId !== ticket.expectedUserId ||
      result.reauthenticationRequired !== true
    ) {
      await finishPasswordChangeUnknown(ticket, null);
      return;
    }

    invalidateRead();
    await clearPrivateData(queryClient, apiClient);
    if (!isTicketCurrent(ticket)) return;

    activeTicket = undefined;
    deferredRevalidation = false;
    hasConfirmedSession = false;
    validatedUser = null;
    manualOnlyRevalidation = false;
    publish({
      state: { kind: "anonymous" },
      pendingAction: null,
      error: null,
      notice: "password-changed-login-again",
      privacyCovered: false,
    });
    sessionEvents.publish();
  }

  async function finishPasswordChangeUnknown(
    ticket: MutationTicket,
    error: ApiError | null,
  ): Promise<void> {
    if (!isTicketCurrent(ticket)) return;

    invalidateRead();
    await clearPrivateData(queryClient, apiClient);
    if (!isTicketCurrent(ticket)) return;

    activeTicket = undefined;
    deferredRevalidation = false;
    hasConfirmedSession = false;
    validatedUser = null;
    manualOnlyRevalidation = true;
    publish({
      state: {
        kind: "unavailable",
        reason: "password-unknown",
        sessionMayExist: true,
      },
      pendingAction: null,
      error,
      notice: null,
      privacyCovered: false,
    });
    sessionEvents.publish();
  }

  function passwordTicketMatchesCurrentActor(ticket: MutationTicket): boolean {
    const current = stateUser(snapshot.state);
    return Boolean(
      ticket.expectedUserId &&
      current?.id === ticket.expectedUserId &&
      ((ticket.passwordMode === "required" &&
        snapshot.state.kind === "password-required") ||
        (ticket.passwordMode === "voluntary" &&
          snapshot.state.kind === "authenticated")),
    );
  }

  async function failPasswordChange(
    ticket: MutationTicket,
    error: ApiError,
  ): Promise<void> {
    if (ticket.kind !== "password" || !isTicketCurrent(ticket)) return;

    if (isHttpError(error, 401)) {
      invalidateRead();
      await clearPrivateData(queryClient, apiClient);
      if (!isTicketCurrent(ticket)) return;
      activeTicket = undefined;
      deferredRevalidation = false;
      hasConfirmedSession = false;
      validatedUser = null;
      manualOnlyRevalidation = false;
      publish({
        state: { kind: "anonymous" },
        pendingAction: null,
        error: null,
        notice: "session-ended",
        privacyCovered: false,
      });
      sessionEvents.publish();
      return;
    }

    if (isHttpError(error, 403) && error.code !== "CSRF_TOKEN_INVALID") {
      activeTicket = undefined;
      deferredRevalidation = false;
      await clearAndPublishRestricted("forbidden", error, undefined, true);
      return;
    }

    const recoverableFailure =
      error.status === 400 ||
      isHttpError(error, 409, "STAFF_PROFILE_VERSION_CONFLICT") ||
      error.code === "CSRF_TOKEN_INVALID" ||
      error.status === 429;

    if (recoverableFailure) {
      const currentState = snapshot.state;
      if (!passwordTicketMatchesCurrentActor(ticket)) {
        await finishPasswordChangeUnknown(ticket, error);
        return;
      }
      activeTicket = undefined;
      if (error.status === 429 && error.retryAfterSeconds !== undefined) {
        passwordRetryAt = Math.max(
          passwordRetryAt,
          now() + error.retryAfterSeconds * 1000,
        );
      }
      publish({
        state: currentState,
        pendingAction: null,
        error,
        notice: null,
        privacyCovered: false,
      });
      finishDeferredRevalidation();
      return;
    }

    await finishPasswordChangeUnknown(ticket, error);
  }

  async function logout(): Promise<void> {
    if (
      snapshot.pendingAction !== null ||
      now() < logoutRetryAt ||
      snapshot.state.kind === "anonymous" ||
      snapshot.state.kind === "checking" ||
      snapshot.state.kind === "signing-out"
    ) {
      return;
    }

    const logoutId = ++logoutSequence;
    activeLogout = logoutId;
    invalidateRead();
    manualOnlyRevalidation = true;
    publish({
      state: { kind: "signing-out" },
      pendingAction: "logout",
      error: null,
      notice: null,
      privacyCovered: true,
    });
    hasConfirmedSession = false;
    validatedUser = null;
    await clearPrivateData(queryClient, apiClient);
    if (activeLogout !== logoutId) return;

    try {
      await identityApi.logout();
      if (activeLogout !== logoutId) return;
      activeLogout = undefined;
      deferredRevalidation = false;
      await clearPrivateData(queryClient, apiClient);
      hasConfirmedSession = false;
      validatedUser = null;
      manualOnlyRevalidation = false;
      publish({
        state: { kind: "anonymous" },
        pendingAction: null,
        error: null,
        notice: "logout-complete",
        privacyCovered: false,
      });
      sessionEvents.publish();
    } catch (caught) {
      if (activeLogout !== logoutId) return;
      activeLogout = undefined;
      deferredRevalidation = false;
      const error = safeApiError(caught);
      if (error && isHttpError(error, 401)) {
        await clearPrivateData(queryClient, apiClient);
        hasConfirmedSession = false;
        validatedUser = null;
        manualOnlyRevalidation = false;
        publish({
          state: { kind: "anonymous" },
          pendingAction: null,
          error: null,
          notice: "logout-already-ended",
          privacyCovered: false,
        });
        sessionEvents.publish();
      } else if (error?.code === "CSRF_TOKEN_INVALID") {
        manualOnlyRevalidation = true;
        publish({
          state: {
            kind: "restricted",
            reason: "forbidden",
            sessionMayExist: true,
          },
          pendingAction: null,
          error,
          notice: null,
          privacyCovered: false,
        });
      } else {
        if (error?.status === 429 && error.retryAfterSeconds !== undefined) {
          logoutRetryAt = now() + error.retryAfterSeconds * 1000;
        }
        manualOnlyRevalidation = true;
        publish({
          state: {
            kind: "unavailable",
            reason: "logout-unknown",
            sessionMayExist: true,
          },
          pendingAction: null,
          error,
          notice: "logout-uncertain",
          privacyCovered: false,
        });
        sessionEvents.publish();
      }
    }
  }

  function onFocus(): void {
    if (visibilityEvents.isVisible()) void revalidate("focus");
  }

  function onVisibilityChange(): void {
    const visible = visibilityEvents.isVisible();
    if (!visible) {
      hidden = true;
      needsVisibleRevalidation =
        needsVisibleRevalidation ||
        hasPrivateState(snapshot.state) ||
        snapshot.state.kind === "checking";
      if (hasPrivateState(snapshot.state)) publish({ privacyCovered: true });
      if (snapshot.pendingAction === null) invalidateRead();
      return;
    }

    const wasHidden = hidden;
    hidden = false;
    if (wasHidden && needsVisibleRevalidation) {
      needsVisibleRevalidation = false;
      void revalidate("visible");
    }
  }

  function onExternalSessionChange(): void {
    if (snapshot.pendingAction !== null) {
      deferredRevalidation = true;
      if (hasPrivateState(snapshot.state)) publish({ privacyCovered: true });
      return;
    }
    if (hasPrivateState(snapshot.state)) publish({ privacyCovered: true });
    void revalidate("external");
  }

  function start(): () => void {
    if (mounted) return () => undefined;
    mounted = true;
    hidden = !visibilityEvents.isVisible();
    visibilityEvents.addFocusListener(onFocus);
    visibilityEvents.addVisibilityListener(onVisibilityChange);
    const stopSessionEvents = sessionEvents.subscribe(onExternalSessionChange);
    unsubscribeRuntime = () => {
      visibilityEvents.removeFocusListener(onFocus);
      visibilityEvents.removeVisibilityListener(onVisibilityChange);
      stopSessionEvents();
    };
    void revalidate("initial");

    return () => {
      if (!mounted) return;
      mounted = false;
      invalidateRead();
      activeTicket = undefined;
      activeLogout = undefined;
      unsubscribeRuntime?.();
      unsubscribeRuntime = undefined;
    };
  }

  return {
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    getSnapshot: () => snapshot,
    start,
    revalidate,
    beginCredentialAction,
    beginPublicIdentityAction,
    finishPublicIdentityAction,
    isTicketCurrent,
    completeLogin,
    failLogin,
    sendLogin,
    completePasswordChange,
    failPasswordChange,
    logout,
  };
}
