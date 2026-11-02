import type { BranchCatalogQuery } from "../../shared/api/organization-contracts";

export interface OrganizationReadScope {
  actorId: string;
  authority: string;
  contextEpoch: number;
}

export function organizationQueryKey(scope: OrganizationReadScope) {
  return [
    "private",
    "organization",
    scope.actorId,
    scope.authority,
    scope.contextEpoch,
  ] as const;
}

export function branchCatalogQueryKey(
  scope: OrganizationReadScope,
  query: BranchCatalogQuery,
) {
  return [
    "private",
    "branch-catalog",
    scope.actorId,
    scope.authority,
    scope.contextEpoch,
    query,
  ] as const;
}

export function branchDetailQueryKey(
  scope: OrganizationReadScope,
  branchId: string,
) {
  return [
    "private",
    "branch-detail",
    scope.actorId,
    scope.authority,
    scope.contextEpoch,
    branchId,
  ] as const;
}
