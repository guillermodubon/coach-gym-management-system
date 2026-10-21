package io.github.guillermodubon.coachgym.auth.web;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.NullableAuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "CurrentUserResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CurrentUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Non-null staff role codes.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        StaffScopeType organizationScope,
        @Schema(description = "Present with null when no active branch is selected or available.",
                implementation = NullableAuthorizedBranchSummary.class,
                requiredMode = Schema.RequiredMode.REQUIRED)
        AuthorizedBranchSummary activeBranch,
        @Schema(description = "Non-null active branches available to the authenticated staff account.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<AuthorizedBranchSummary> availableBranches,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean passwordChangeRequired) {

    public CurrentUserResponse {
        roles = List.copyOf(roles);
        availableBranches = List.copyOf(availableBranches);
    }

    /** Backward-compatible constructor for callers that only need identity and roles. */
    public CurrentUserResponse(UUID id, String username, String fullName, List<String> roles) {
        this(id, username, fullName, roles, null, null, List.of(), false);
    }

    public CurrentUserResponse(
            UUID id,
            String username,
            String fullName,
            List<String> roles,
            StaffScopeType organizationScope,
            AuthorizedBranchSummary activeBranch,
            List<AuthorizedBranchSummary> availableBranches) {
        this(id, username, fullName, roles, organizationScope, activeBranch, availableBranches, false);
    }
}
