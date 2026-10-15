package io.github.guillermodubon.coachgym.plan.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Administrative projection of a plan's mutable branch-coverage definition. */
@Schema(
        name = "PlanBranchCoverageResponse",
        description = "Current plan sale-eligibility definition; it does not rewrite purchased membership-period snapshots.")
public record PlanBranchCoverageResponse(
        @JsonProperty("scope")
        @Schema(description = "Coverage semantics used when selling this plan.")
        MembershipPlanBranchCoverageScope scope,

        @JsonProperty("branchIds")
        @Schema(description = "Explicit active branch IDs for SINGLE_BRANCH or SELECTED_BRANCHES; empty for ALL_BRANCHES.")
        List<UUID> branchIds,

        @JsonProperty("version")
        @Schema(description = "Current optimistic-lock version.", minimum = "0")
        long version) {

    public PlanBranchCoverageResponse {
        branchIds = branchIds.stream().sorted().toList();
    }

    static PlanBranchCoverageResponse from(MembershipPlanBranchCoverageDetails details) {
        return new PlanBranchCoverageResponse(
                details.scope(),
                details.branchIds().stream().sorted().toList(),
                details.version());
    }
}
