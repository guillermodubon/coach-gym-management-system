package io.github.guillermodubon.coachgym.plan;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Pure cardinality and normalization rules for plan coverage and snapshots. */
public final class MembershipPlanBranchCoveragePolicy {

    private MembershipPlanBranchCoveragePolicy() {
    }

    /**
     * Validates a plan definition and returns its sorted, immutable explicit
     * branch set. {@code ALL_BRANCHES} is dynamic at plan-definition time and
     * therefore carries no explicit branch IDs.
     */
    public static Set<UUID> normalizePlanDefinition(
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> branchIds) {

        Set<UUID> normalized = normalize(scope, branchIds);
        int size = normalized.size();
        switch (scope) {
            case SINGLE_BRANCH -> require(size == 1,
                    "SINGLE_BRANCH coverage requires exactly one branch.");
            case SELECTED_BRANCHES -> require(size >= 2,
                    "SELECTED_BRANCHES coverage requires at least two branches.");
            case ALL_BRANCHES -> require(size == 0,
                    "ALL_BRANCHES plan coverage must not contain explicit branches.");
        }
        return normalized;
    }

    /**
     * Validates and normalizes the finite branch entitlement captured for a
     * membership period. Even an {@code ALL_BRANCHES} plan is captured as the
     * exact non-empty active branch set available at sale or renewal time.
     */
    public static Set<UUID> normalizePeriodSnapshot(
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> branchIds) {

        Set<UUID> normalized = normalize(scope, branchIds);
        int size = normalized.size();
        switch (scope) {
            case SINGLE_BRANCH -> require(size == 1,
                    "SINGLE_BRANCH snapshot requires exactly one branch.");
            case SELECTED_BRANCHES -> require(size >= 2,
                    "SELECTED_BRANCHES snapshot requires at least two branches.");
            case ALL_BRANCHES -> require(size >= 1,
                    "ALL_BRANCHES snapshot requires at least one active branch.");
        }
        return normalized;
    }

    private static Set<UUID> normalize(
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> branchIds) {

        if (scope == null) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Membership plan branch-coverage scope is required.");
        }
        if (branchIds == null) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Branch IDs are required.");
        }

        TreeSet<UUID> sorted = new TreeSet<>();
        for (UUID branchId : branchIds) {
            if (branchId == null) {
                throw new MembershipPlanBranchCoverageValidationException(
                        "Branch IDs cannot contain null values.");
            }
            sorted.add(branchId);
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new MembershipPlanBranchCoverageValidationException(message);
        }
    }
}
