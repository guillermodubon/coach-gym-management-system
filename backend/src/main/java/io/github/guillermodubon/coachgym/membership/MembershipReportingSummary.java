package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Bounded membership and period counts with safe latest-plan and immutable
 * purchased-coverage distributions.
 */
public record MembershipReportingSummary(
        long activeMemberships,
        long frozenMemberships,
        long expiredMemberships,
        long cancelledMemberships,
        long newMemberships,
        long activePeriods,
        long newPeriods,
        long expiringPeriods,
        List<PlanDistribution> planDistribution,
        List<CoverageDistribution> coverageDistribution) {

    public MembershipReportingSummary {
        nonNegative(activeMemberships, "Active membership count");
        nonNegative(frozenMemberships, "Frozen membership count");
        nonNegative(expiredMemberships, "Expired membership count");
        nonNegative(cancelledMemberships, "Cancelled membership count");
        nonNegative(newMemberships, "New membership count");
        nonNegative(activePeriods, "Active period count");
        nonNegative(newPeriods, "New period count");
        nonNegative(expiringPeriods, "Expiring period count");
        Objects.requireNonNull(planDistribution, "Plan distribution is required.");
        Objects.requireNonNull(coverageDistribution, "Coverage distribution is required.");
        if (planDistribution.stream().anyMatch(Objects::isNull)
                || coverageDistribution.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Membership distributions must not contain null.");
        }
        List<PlanDistribution> plans = new ArrayList<>(planDistribution);
        plans.sort(Comparator.comparing(PlanDistribution::planCode));
        planDistribution = List.copyOf(plans);
        List<CoverageDistribution> coverage = new ArrayList<>(coverageDistribution);
        coverage.sort(Comparator.comparing(value -> value.scope().name()));
        coverageDistribution = List.copyOf(coverage);
    }

    public record PlanDistribution(String planCode, String planName, long membershipCount) {
        public PlanDistribution {
            if (planCode == null || planCode.isBlank() || planName == null || planName.isBlank()) {
                throw new IllegalArgumentException("Membership plan snapshot labels are required.");
            }
            planCode = planCode.strip();
            planName = planName.strip();
            nonNegative(membershipCount, "Membership plan count");
        }
    }

    public record CoverageDistribution(
            MembershipPlanBranchCoverageScope scope,
            long membershipCount) {
        public CoverageDistribution {
            Objects.requireNonNull(scope, "Membership coverage scope is required.");
            nonNegative(membershipCount, "Membership coverage count");
        }
    }

    private static void nonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative.");
        }
    }
}
