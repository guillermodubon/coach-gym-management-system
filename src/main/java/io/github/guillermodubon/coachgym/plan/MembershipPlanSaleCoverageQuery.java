package io.github.guillermodubon.coachgym.plan;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read boundary for obtaining the exact active plan coverage used by
 * a membership sale or renewal.
 */
@FunctionalInterface
public interface MembershipPlanSaleCoverageQuery {

    /**
     * Returns empty if the plan is inactive, the registration branch is
     * inactive/non-canonical, or the plan does not cover that branch.
     * Implementations must hold consistency locks through the caller's
     * transaction while reading the plan definition and finite branch set.
     */
    Optional<MembershipPlanSaleCoverage> findForSale(
            UUID planId,
            UUID registrationBranchId);
}
