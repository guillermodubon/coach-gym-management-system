package io.github.guillermodubon.coachgym.plan;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Authoritative finite coverage used to create one membership period.
 *
 * <p>Unlike an {@code ALL_BRANCHES} plan definition, this projection always
 * contains the exact active branch set observed for the sale transaction.</p>
 */
public record MembershipPlanSaleCoverage(
        UUID planId,
        MembershipPlanBranchCoverageScope scope,
        Set<UUID> coveredBranchIds,
        long sourcePlanVersion) {

    public MembershipPlanSaleCoverage {
        if (planId == null || scope == null || coveredBranchIds == null) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Plan sale coverage fields are required.");
        }
        if (sourcePlanVersion < 0) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Source plan version must not be negative.");
        }
        TreeSet<UUID> normalized = new TreeSet<>();
        for (UUID branchId : coveredBranchIds) {
            if (branchId == null) {
                throw new MembershipPlanBranchCoverageValidationException(
                        "Covered branch IDs cannot contain null values.");
            }
            normalized.add(branchId);
        }
        int count = normalized.size();
        boolean validCardinality = switch (scope) {
            case SINGLE_BRANCH -> count == 1;
            case SELECTED_BRANCHES -> count >= 2;
            case ALL_BRANCHES -> count >= 1;
        };
        if (!validCardinality) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Plan sale coverage has invalid branch cardinality.");
        }
        coveredBranchIds = Collections.unmodifiableSet(normalized);
    }

    public boolean includes(UUID branchId) {
        return branchId != null && coveredBranchIds.contains(branchId);
    }
}
