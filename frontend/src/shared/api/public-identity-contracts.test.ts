import { describe, expect, it } from "vitest";
import { ApiError } from "./errors";
import {
  parsePasswordRecoveryAcknowledgement,
  parseStaffInvitationAcceptance,
  parseStaffInvitationPreview,
} from "./public-identity-contracts";

const branch = {
  branchId: "550e8400-e29b-41d4-a716-446655440001",
  code: "CENTRAL",
  name: "Sucursal central",
};
const invitation = {
  invitationId: "550e8400-e29b-41d4-a716-446655440000",
  maskedEmail: "a***@example.test",
  proposedRole: "RECEPTIONIST",
  proposedScope: "BRANCH",
  proposedBranches: [branch],
  expiresAt: "2026-10-21T12:00:00Z",
};

describe("public identity response parsers", () => {
  it("accepts only the DTO-backed invitation preview shape and valid authority proposals", () => {
    expect(parseStaffInvitationPreview(invitation)).toEqual(invitation);
    expect(
      parseStaffInvitationPreview({
        ...invitation,
        proposedRole: "ADMIN",
        proposedScope: "ORGANIZATION",
        proposedBranches: [],
      }).proposedBranches,
    ).toEqual([]);
    expect(() =>
      parseStaffInvitationPreview({
        ...invitation,
        maskedEmail: "person@example.test",
      }),
    ).toThrow(ApiError);
    expect(() =>
      parseStaffInvitationPreview({
        ...invitation,
        proposedRole: "RECEPTIONIST",
        proposedScope: "ORGANIZATION",
        proposedBranches: [],
      }),
    ).toThrow(ApiError);
    expect(() =>
      parseStaffInvitationPreview({
        ...invitation,
        invitationId: "internal-id",
      }),
    ).toThrow(ApiError);
  });

  it("validates acceptance output without adding a session or identity fields", () => {
    const result = {
      userId: "550e8400-e29b-41d4-a716-446655440002",
      role: "RECEPTIONIST",
      scope: "BRANCH",
      branchIds: [branch.branchId],
      acceptedAt: "2026-10-20T12:00:00Z",
    };
    expect(parseStaffInvitationAcceptance(result)).toEqual(result);
    expect(() =>
      parseStaffInvitationAcceptance({
        ...result,
        scope: "ORGANIZATION",
        branchIds: [],
      }),
    ).toThrow(ApiError);
  });

  it("validates the generic recovery acknowledgement without interpreting it", () => {
    expect(
      parsePasswordRecoveryAcknowledgement({
        message: "Generic acknowledgement",
      }),
    ).toEqual({ message: "Generic acknowledgement" });
    expect(() => parsePasswordRecoveryAcknowledgement({ message: "" })).toThrow(
      ApiError,
    );
  });
});
