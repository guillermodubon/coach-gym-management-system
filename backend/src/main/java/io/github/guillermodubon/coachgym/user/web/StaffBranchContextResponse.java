package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.NullableAuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Server-authoritative active branch context for the authenticated staff user. */
@Schema(name = "StaffBranchContextResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffBranchContextResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID organizationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        StaffScopeType scopeType,
        @Schema(description = "Present with null when no active branch is selected.",
                implementation = NullableAuthorizedBranchSummary.class,
                requiredMode = Schema.RequiredMode.REQUIRED)
        AuthorizedBranchSummary activeBranch,
        @Schema(description = "Non-null active branches available to the authenticated staff account.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<AuthorizedBranchSummary> availableBranches) {

    static StaffBranchContextResponse from(StaffBranchContext context) {
        AuthorizedBranchSummary active = context.availableBranches().stream()
                .filter(branch -> branch.id().equals(context.activeBranchId()))
                .findFirst()
                .orElse(null);
        return new StaffBranchContextResponse(
                context.organizationId(), context.scopeType(), active, context.availableBranches());
    }
}
