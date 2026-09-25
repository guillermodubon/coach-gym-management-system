package io.github.guillermodubon.coachgym.plan;

import java.util.Set;
import java.util.UUID;

/** Immutable, technology-neutral branch-coverage projection for a plan. */
public record MembershipPlanBranchCoverageDetails(
        UUID planId,
        MembershipPlanBranchCoverageScope scope,
        Set<UUID> branchIds,
        long version) {

    public MembershipPlanBranchCoverageDetails {
        if (planId == null) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Plan ID is required.");
        }
        if (version < 0) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Plan coverage version must not be negative.");
        }
        branchIds = MembershipPlanBranchCoveragePolicy
                .normalizePlanDefinition(scope, branchIds);
    }
}
