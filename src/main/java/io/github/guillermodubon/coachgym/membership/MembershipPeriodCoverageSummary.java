package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;

/** Privacy-minimized summary of one immutable membership-period entitlement. */
public record MembershipPeriodCoverageSummary(
        MembershipPlanBranchCoverageScope scopeSnapshot,
        int coveredBranchCount) {

    public MembershipPeriodCoverageSummary {
        if (scopeSnapshot == null || coveredBranchCount < 1) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Membership-period coverage summary is invalid.");
        }
        boolean validCardinality = switch (scopeSnapshot) {
            case SINGLE_BRANCH -> coveredBranchCount == 1;
            case SELECTED_BRANCHES -> coveredBranchCount >= 2;
            case ALL_BRANCHES -> coveredBranchCount >= 1;
        };
        if (!validCardinality) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Membership-period coverage summary has invalid scope cardinality.");
        }
    }
}
