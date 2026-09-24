package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageValidationException;
import io.github.guillermodubon.coachgym.plan.UpdateMembershipPlanBranchCoverageCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Allowlisted request to atomically replace a plan's branch-coverage definition. */
@Schema(
        name = "UpdatePlanBranchCoverageRequest",
        description = "Complete coverage replacement; only active canonical branches are accepted.")
record UpdatePlanBranchCoverageRequest(
        @NotNull
        @Schema(description = "Coverage semantics. SINGLE_BRANCH requires one branch, SELECTED_BRANCHES at least two, and ALL_BRANCHES an empty branchIds list.")
        MembershipPlanBranchCoverageScope scope,

        @NotNull
        @Schema(description = "Complete branch set. Empty only for ALL_BRANCHES; IDs never grant authority.")
        List<@NotNull UUID> branchIds,

        @Min(0)
        @Schema(description = "Current optimistic-lock version returned by the coverage endpoint.", minimum = "0")
        long expectedVersion) {

    UpdatePlanBranchCoverageRequest {
        if (branchIds != null) {
            branchIds = java.util.Collections.unmodifiableList(new ArrayList<>(branchIds));
        }
    }

    UpdateMembershipPlanBranchCoverageCommand toCommand() {
        if (scope == null || branchIds == null
                || branchIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(branchIds).size() != branchIds.size()) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "A valid coverage scope and unique branch identifiers are required.");
        }
        return new UpdateMembershipPlanBranchCoverageCommand(
                scope,
                Set.copyOf(branchIds),
                expectedVersion);
    }
}
