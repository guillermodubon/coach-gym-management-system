package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.swagger.v3.oas.annotations.media.Schema;

/** Safe summary of purchased-period branch entitlement; never returns branch IDs. */
@Schema(
        name = "MembershipPeriodCoverageResponse",
        description = "Immutable entitlement captured at sale or renewal; only scope and cardinality are exposed.")
record MembershipPeriodCoverageResponse(
        @Schema(description = "Coverage scope captured for this membership period.")
        MembershipPlanBranchCoverageScope scopeSnapshot,

        @Schema(description = "Number of branches in this period's immutable entitlement; branch IDs are not exposed.", minimum = "1")
        int coveredBranchCount) {

    static MembershipPeriodCoverageResponse from(MembershipPeriodCoverageSummary summary) {
        if (summary == null) {
            return null;
        }
        return new MembershipPeriodCoverageResponse(
                summary.scopeSnapshot(),
                summary.coveredBranchCount());
    }
}
