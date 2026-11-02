import { describe, expect, it } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import type { AuthSnapshot } from "../auth/auth-model";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { canManageOrganizationBranches } from "./branch-editor-policy";

function snapshot(
  user: CurrentUser | null = identityFixture as CurrentUser,
  overrides: Partial<AuthSnapshot> = {},
): AuthSnapshot {
  return {
    state: user ? { kind: "authenticated", user } : { kind: "anonymous" },
    pendingAction: null,
    error: null,
    notice: null,
    privacyCovered: false,
    contextEpoch: 0,
    branchContextTransition: {
      status: "idle",
      notice: null,
      error: null,
    },
    revision: 0,
    ...overrides,
  };
}

describe("branch administration capability", () => {
  it("requires the single ADMIN role and persisted organization scope", () => {
    expect(canManageOrganizationBranches(snapshot())).toBe(true);
    expect(
      canManageOrganizationBranches(
        snapshot({
          ...(identityFixture as CurrentUser),
          roles: ["ADMIN"],
          organizationScope: "BRANCH",
          availableBranches: [branchFixture],
        }),
      ),
    ).toBe(false);
    expect(
      canManageOrganizationBranches(
        snapshot({
          ...(identityFixture as CurrentUser),
          roles: ["RECEPTIONIST"],
          organizationScope: "BRANCH",
          availableBranches: [branchFixture],
        }),
      ),
    ).toBe(false);
  });

  it("covers anonymous, checking, pending and privacy-covered sessions", () => {
    expect(canManageOrganizationBranches(snapshot(null))).toBe(false);
    expect(
      canManageOrganizationBranches(
        snapshot(identityFixture as CurrentUser, {
          state: { kind: "checking" },
        }),
      ),
    ).toBe(false);
    expect(
      canManageOrganizationBranches(
        snapshot(identityFixture as CurrentUser, { pendingAction: "logout" }),
      ),
    ).toBe(false);
    expect(
      canManageOrganizationBranches(
        snapshot(identityFixture as CurrentUser, { privacyCovered: true }),
      ),
    ).toBe(false);
  });
});
