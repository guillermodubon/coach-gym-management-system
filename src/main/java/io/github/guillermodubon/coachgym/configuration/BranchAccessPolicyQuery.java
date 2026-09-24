package io.github.guillermodubon.coachgym.configuration;

import java.util.UUID;

/** Public query boundary for the effective payment policy at a physical branch. */
@FunctionalInterface
public interface BranchAccessPolicyQuery {

    /** Resolves organization default and branch override for the requested branch. */
    EffectiveBranchAccessPolicy findForBranch(
            UUID organizationId,
            UUID branchId);
}
