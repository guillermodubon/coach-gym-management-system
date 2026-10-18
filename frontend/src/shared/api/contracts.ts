import type { components } from "./generated/schema";
import { ApiError, isRecord } from "./errors";

type Schemas = components["schemas"];
export type LoginRequest = Schemas["LoginRequest"];
export type ChangePasswordRequest = Schemas["ChangeStaffPasswordRequest"];
export type AuthorizedBranch = Required<Schemas["AuthorizedBranchSummary"]>;
export type CurrentUser = Omit<
  Required<Schemas["CurrentUserResponse"]>,
  "activeBranch" | "availableBranches"
> & {
  activeBranch: AuthorizedBranch | null;
  availableBranches: AuthorizedBranch[];
};
export type BranchContext = Omit<
  Required<Schemas["StaffBranchContextResponse"]>,
  "activeBranch" | "availableBranches"
> & {
  activeBranch: AuthorizedBranch | null;
  availableBranches: AuthorizedBranch[];
};
export type PasswordChangeResult = Required<
  Schemas["StaffPasswordChangeResponse"]
>;
export type CsrfToken = Required<Schemas["CsrfTokenResponse"]>;

function invalid(): never {
  throw new ApiError("invalid-response");
}

function text(value: unknown): string {
  return typeof value === "string" ? value : invalid();
}

function uuid(value: unknown): string {
  const result = text(value);
  return /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(result)
    ? result
    : invalid();
}

function bool(value: unknown): boolean {
  return typeof value === "boolean" ? value : invalid();
}

function scope(value: unknown): "ORGANIZATION" | "BRANCH" {
  if (value === "ORGANIZATION" || value === "BRANCH") return value;
  if (typeof value === "string") {
    throw new ApiError("invalid-response", {
      clientReason: "unsupported-authority",
    });
  }
  return invalid();
}

function branch(value: unknown): AuthorizedBranch {
  if (!isRecord(value)) return invalid();
  return {
    id: uuid(value.id),
    organizationId: uuid(value.organizationId),
    code: text(value.code),
    name: text(value.name),
    timezone: text(value.timezone),
    initialBranch: bool(value.initialBranch),
  };
}

function branches(value: unknown): AuthorizedBranch[] {
  return Array.isArray(value) ? value.map(branch) : invalid();
}

export function parseCsrf(value: unknown): CsrfToken {
  if (!isRecord(value)) return invalid();
  const token = text(value.token);
  const headerName = text(value.headerName);
  const parameterName = text(value.parameterName);
  if (
    !/^[\x21-\x7e]{1,8192}$/.test(token) ||
    !/^X-[A-Za-z][A-Za-z\d-]{0,61}$/i.test(headerName) ||
    !/^[A-Za-z_][A-Za-z\d_-]{0,127}$/.test(parameterName)
  )
    return invalid();
  return { token, headerName, parameterName };
}

export function parseCurrentUser(value: unknown): CurrentUser {
  if (!isRecord(value) || !Array.isArray(value.roles)) return invalid();
  return {
    id: uuid(value.id),
    username: text(value.username),
    fullName: text(value.fullName),
    roles: value.roles.map(text),
    organizationScope: scope(value.organizationScope),
    activeBranch:
      value.activeBranch == null ? null : branch(value.activeBranch),
    availableBranches: branches(value.availableBranches),
    passwordChangeRequired: bool(value.passwordChangeRequired),
  };
}

export function parseBranchContext(value: unknown): BranchContext {
  if (!isRecord(value)) return invalid();
  return {
    organizationId: uuid(value.organizationId),
    scopeType: scope(value.scopeType),
    activeBranch:
      value.activeBranch == null ? null : branch(value.activeBranch),
    availableBranches: branches(value.availableBranches),
  };
}

export function parsePasswordChange(value: unknown): PasswordChangeResult {
  if (
    !isRecord(value) ||
    !Number.isSafeInteger(value.profileVersion) ||
    Number(value.profileVersion) < 0
  )
    return invalid();
  return {
    userId: uuid(value.userId),
    profileVersion: Number(value.profileVersion),
    reauthenticationRequired: bool(value.reauthenticationRequired),
  };
}
