package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Pure normalization and cardinality rules for purchased period snapshots. */
public final class MembershipPeriodBranchCoveragePolicy {

    private MembershipPeriodBranchCoveragePolicy() {
    }

    /**
     * Returns a sorted immutable set representing the exact branch entitlement
     * captured for a period. An {@code ALL_BRANCHES} plan must supply its finite
     * active branch set at capture time.
     */
    public static Set<UUID> normalizeSnapshot(
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> branchIds) {

        if (scope == null) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Coverage scope snapshot is required.");
        }
        if (branchIds == null) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Covered branch IDs are required.");
        }

        TreeSet<UUID> sorted = new TreeSet<>();
        for (UUID branchId : branchIds) {
            if (branchId == null) {
                throw new MembershipPeriodBranchCoverageValidationException(
                        "Covered branch IDs cannot contain null values.");
            }
            sorted.add(branchId);
        }

        int size = sorted.size();
        switch (scope) {
            case SINGLE_BRANCH -> require(size == 1,
                    "SINGLE_BRANCH snapshot requires exactly one branch.");
            case SELECTED_BRANCHES -> require(size >= 2,
                    "SELECTED_BRANCHES snapshot requires at least two branches.");
            case ALL_BRANCHES -> require(size >= 1,
                    "ALL_BRANCHES snapshot requires at least one active branch.");
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new MembershipPeriodBranchCoverageValidationException(message);
        }
    }
}
