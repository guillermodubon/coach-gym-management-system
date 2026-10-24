import type { CurrentUser } from "../../shared/api/contracts";
import type { ApiError } from "../../shared/api/errors";

export type AuthState =
  | { kind: "checking" }
  | { kind: "anonymous" }
  | { kind: "authenticated"; user: Readonly<CurrentUser> }
  | { kind: "password-required"; user: Readonly<CurrentUser> }
  | {
      kind: "restricted";
      reason:
        | "unsupported-authority"
        | "branch-context"
        | "forbidden"
        | "inconsistent-context";
      sessionMayExist: boolean;
    }
  | {
      kind: "unavailable";
      reason:
        | "connection"
        | "after-login"
        | "logout-unknown"
        | "password-unknown"
        | "invalid-response";
      sessionMayExist: boolean;
    }
  | { kind: "signing-out" };

export type AuthNotice =
  | "session-ended"
  | "login-not-established"
  | "login-uncertain"
  | "logout-complete"
  | "logout-already-ended"
  | "logout-uncertain"
  | "password-changed-login-again"
  | null;

export interface AuthSnapshot {
  readonly state: AuthState;
  readonly pendingAction:
    "login" | "logout" | "password" | "public-identity" | null;
  readonly error: ApiError | null;
  readonly notice: AuthNotice;
  readonly privacyCovered: boolean;
  readonly revision: number;
}

export interface MutationTicket {
  readonly id: number;
  readonly kind: "login" | "password" | "public-identity";
  readonly signal: AbortSignal;
  readonly expectedUserId?: string;
  readonly passwordMode?: "required" | "voluntary";
  readonly publicAction?:
    | "recovery-request"
    | "recovery-complete"
    | "invitation-inspect"
    | "invitation-accept";
}

export type AuthorityValidation =
  | { ok: true }
  | {
      ok: false;
      reason: "unsupported-authority" | "inconsistent-context";
    };

export function validateAuthority(user: CurrentUser): AuthorityValidation {
  if (user.roles.length !== 1) {
    return { ok: false, reason: "unsupported-authority" };
  }

  const role = user.roles[0];
  const supported =
    (role === "ADMIN" &&
      (user.organizationScope === "ORGANIZATION" ||
        user.organizationScope === "BRANCH")) ||
    (role === "RECEPTIONIST" && user.organizationScope === "BRANCH");
  if (!supported) return { ok: false, reason: "unsupported-authority" };

  const branchIds = new Set(user.availableBranches.map(({ id }) => id));
  const organizationIds = new Set(
    user.availableBranches.map(({ organizationId }) => organizationId),
  );
  if (
    branchIds.size !== user.availableBranches.length ||
    organizationIds.size > 1 ||
    (user.organizationScope === "BRANCH" && branchIds.size === 0)
  ) {
    return { ok: false, reason: "inconsistent-context" };
  }

  if (user.activeBranch) {
    const authorized = user.availableBranches.find(
      ({ id }) => id === user.activeBranch?.id,
    );
    if (
      !authorized ||
      authorized.organizationId !== user.activeBranch.organizationId ||
      authorized.code !== user.activeBranch.code ||
      authorized.name !== user.activeBranch.name ||
      authorized.timezone !== user.activeBranch.timezone ||
      authorized.initialBranch !== user.activeBranch.initialBranch
    ) {
      return { ok: false, reason: "inconsistent-context" };
    }
  }

  return { ok: true };
}
