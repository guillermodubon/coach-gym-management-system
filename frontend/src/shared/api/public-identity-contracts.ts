import type { components } from "./generated/f02-schema";
import { ApiError, isRecord } from "./errors";

type Schemas = components["schemas"];
type RequiredFields<T, K extends keyof T> = Required<Pick<T, K>>;

export type PasswordRecoveryRequest = Schemas["RequestPasswordRecoveryRequest"];
export type PasswordRecoveryCompletion =
  Schemas["CompletePasswordRecoveryRequest"];
export type StaffInvitationAcceptanceRequest =
  Schemas["AcceptStaffInvitationRequest"];
export type PasswordRecoveryAcknowledgement = RequiredFields<
  Schemas["PasswordRecoveryAcknowledgement"],
  "message"
>;
export type StaffInvitationAcceptance = RequiredFields<
  Schemas["StaffInvitationAcceptanceResponse"],
  "userId" | "role" | "scope" | "branchIds" | "acceptedAt"
>;

export type StaffInvitationRole = "ADMIN" | "RECEPTIONIST";
export type StaffInvitationScope = "ORGANIZATION" | "BRANCH";

export interface StaffInvitationBranchPreview {
  branchId: string;
  code: string;
  name: string;
}

/** Client decoder for the DTO-backed H09 body; OpenAPI intentionally has no schema here. */
export interface StaffInvitationPreview {
  invitationId: string;
  maskedEmail: string;
  proposedRole: StaffInvitationRole;
  proposedScope: StaffInvitationScope;
  proposedBranches: StaffInvitationBranchPreview[];
  expiresAt: string;
}

function invalid(): never {
  throw new ApiError("invalid-response");
}

function text(value: unknown, maximum = 512): string {
  return typeof value === "string" &&
    value.length > 0 &&
    value.length <= maximum
    ? value
    : invalid();
}

function uuid(value: unknown): string {
  const result = text(value);
  return /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(result)
    ? result
    : invalid();
}

function timestamp(value: unknown): string {
  const result = text(value);
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(
    result,
  ) && Number.isFinite(Date.parse(result))
    ? result
    : invalid();
}

function role(value: unknown): StaffInvitationRole {
  return value === "ADMIN" || value === "RECEPTIONIST" ? value : invalid();
}

function scope(value: unknown): StaffInvitationScope {
  return value === "ORGANIZATION" || value === "BRANCH" ? value : invalid();
}

function branch(value: unknown): StaffInvitationBranchPreview {
  if (!isRecord(value)) return invalid();
  return {
    branchId: uuid(value.branchId),
    code: text(value.code, 32),
    name: text(value.name, 160),
  };
}

function maskedEmail(value: unknown): string {
  const result = text(value, 254);
  return result === "[masked]" || /^[^\s@]\*\*\*@[^\s@]+$/.test(result)
    ? result
    : invalid();
}

function validateProposal(
  proposedRole: StaffInvitationRole,
  proposedScope: StaffInvitationScope,
  branches: readonly unknown[],
): void {
  const valid =
    (proposedRole === "ADMIN" &&
      proposedScope === "ORGANIZATION" &&
      branches.length === 0) ||
    ((proposedRole === "ADMIN" || proposedRole === "RECEPTIONIST") &&
      proposedScope === "BRANCH" &&
      branches.length > 0);
  if (!valid) invalid();
}

export function parsePasswordRecoveryAcknowledgement(
  value: unknown,
): PasswordRecoveryAcknowledgement {
  if (!isRecord(value)) return invalid();
  return { message: text(value.message, 1_000) };
}

export function parseStaffInvitationPreview(
  value: unknown,
): StaffInvitationPreview {
  if (!isRecord(value) || !Array.isArray(value.proposedBranches))
    return invalid();
  const proposedRole = role(value.proposedRole);
  const proposedScope = scope(value.proposedScope);
  const proposedBranches = value.proposedBranches.map(branch);
  validateProposal(proposedRole, proposedScope, proposedBranches);
  return {
    invitationId: uuid(value.invitationId),
    maskedEmail: maskedEmail(value.maskedEmail),
    proposedRole,
    proposedScope,
    proposedBranches,
    expiresAt: timestamp(value.expiresAt),
  };
}

export function parseStaffInvitationAcceptance(
  value: unknown,
): StaffInvitationAcceptance {
  if (!isRecord(value) || !Array.isArray(value.branchIds)) return invalid();
  const acceptedRole = role(value.role);
  const acceptedScope = scope(value.scope);
  const branchIds = value.branchIds.map(uuid);
  validateProposal(acceptedRole, acceptedScope, branchIds);
  return {
    userId: uuid(value.userId),
    role: acceptedRole,
    scope: acceptedScope,
    branchIds,
    acceptedAt: timestamp(value.acceptedAt),
  };
}
