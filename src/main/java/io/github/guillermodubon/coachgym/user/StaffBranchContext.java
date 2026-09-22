package io.github.guillermodubon.coachgym.user;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Safe branch-context preference resolved for the authenticated staff member. */
public record StaffBranchContext(
        UUID organizationId,
        StaffScopeType scopeType,
        UUID activeBranchId,
        List<AuthorizedBranchSummary> availableBranches) {

    public StaffBranchContext {
        organizationId = StaffAssignmentValuePolicy.requireId(organizationId, "organizationId");
        scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
        availableBranches = List.copyOf(Objects.requireNonNull(availableBranches, "availableBranches is required"));
        Set<UUID> ids = new HashSet<>();
        for (AuthorizedBranchSummary branch : availableBranches) {
            if (branch == null || !ids.add(branch.id())) {
                throw new StaffScopeValidationException("available branches must be unique and non-null");
            }
            if (!organizationId.equals(branch.organizationId())) {
                throw new StaffScopeValidationException("available branches must belong to the organization");
            }
        }
        if (activeBranchId != null && !ids.contains(activeBranchId)) {
            throw new StaffScopeStateConflictException("active branch is not available to this context");
        }
    }
}
