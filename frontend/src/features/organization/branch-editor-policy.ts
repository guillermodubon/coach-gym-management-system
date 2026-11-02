import type { CurrentUser } from "../../shared/api/contracts";
import type { AuthSnapshot } from "../auth/auth-model";

export function getBranchManagementUser(
  auth: AuthSnapshot,
): Readonly<CurrentUser> | null {
  if (
    auth.state.kind !== "authenticated" ||
    auth.pendingAction !== null ||
    auth.privacyCovered
  ) {
    return null;
  }
  const user = auth.state.user;
  return user.roles.length === 1 &&
    user.roles[0] === "ADMIN" &&
    user.organizationScope === "ORGANIZATION"
    ? user
    : null;
}

export function canManageOrganizationBranches(auth: AuthSnapshot): boolean {
  return getBranchManagementUser(auth) !== null;
}
