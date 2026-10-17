package io.github.guillermodubon.coachgym.plan;

import java.util.Set;
import java.util.UUID;

/** Allowlisted request to replace a plan's branch coverage using optimistic locking. */
public record UpdateMembershipPlanBranchCoverageCommand(
        MembershipPlanBranchCoverageScope scope,
        Set<UUID> branchIds,
        long expectedVersion) {

    public UpdateMembershipPlanBranchCoverageCommand {
        if (expectedVersion < 0) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Expected plan coverage version must not be negative.");
        }
        branchIds = MembershipPlanBranchCoveragePolicy
                .normalizePlanDefinition(scope, branchIds);
    }
}
