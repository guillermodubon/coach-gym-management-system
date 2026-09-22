package io.github.guillermodubon.coachgym.auth.web;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        String fullName,
        List<String> roles,
        StaffScopeType organizationScope,
        AuthorizedBranchSummary activeBranch,
        List<AuthorizedBranchSummary> availableBranches) {

    public CurrentUserResponse {
        roles = List.copyOf(roles);
        availableBranches = List.copyOf(availableBranches);
    }

    /** Backward-compatible constructor for callers that only need identity and roles. */
    public CurrentUserResponse(UUID id, String username, String fullName, List<String> roles) {
        this(id, username, fullName, roles, null, null, List.of());
    }
}
