import { useAuth } from "../auth/useAuth";

export function useBranchContext() {
  const auth = useAuth();
  const user = auth.state.kind === "authenticated" ? auth.state.user : null;

  return {
    scopeType: user?.organizationScope ?? null,
    activeBranch: user?.activeBranch ?? null,
    availableBranches: user?.availableBranches ?? [],
    contextEpoch: auth.contextEpoch,
    transition: auth.branchContextTransition,
    selectBranch: auth.selectBranchContext,
    clearBranch: auth.clearBranchContext,
    reconcile: auth.reconcileBranchContext,
  };
}
