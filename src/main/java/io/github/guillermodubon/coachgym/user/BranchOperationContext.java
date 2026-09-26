package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-resolved facts used to authorize one branch-aware operation.
 *
 * <p>{@code authorizedActiveBranchIds} is supplied by an authoritative
 * resolver and therefore contains only active branches currently available to
 * the staff member. {@code activeBranchId} is a server-side preference and may
 * be {@code null} when a branch-scoped operation has not selected a branch.
 * This type deliberately contains no Spring Security, servlet, persistence,
 * or HTTP types.</p>
 */
public record BranchOperationContext(
        UUID userId,
        UUID organizationId,
        StaffScopeType scopeType,
        UUID activeBranchId,
        Set<UUID> authorizedActiveBranchIds) {

    public BranchOperationContext {
        userId = requireId(userId, "userId");
        organizationId = requireId(organizationId, "organizationId");
        scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
        Objects.requireNonNull(authorizedActiveBranchIds, "authorizedActiveBranchIds is required");
        if (authorizedActiveBranchIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("authorizedActiveBranchIds cannot contain nulls");
        }
        authorizedActiveBranchIds = Set.copyOf(authorizedActiveBranchIds);
        if (activeBranchId != null) {
            requireId(activeBranchId, "activeBranchId");
        }
    }

    public boolean organizationWide() {
        return scopeType == StaffScopeType.ORGANIZATION;
    }

    public boolean hasActiveBranch() {
        return activeBranchId != null
                && authorizedActiveBranchIds.contains(activeBranchId);
    }

    public boolean canAddressAuthorizedBranch(UUID branchId) {
        return branchId != null && authorizedActiveBranchIds.contains(branchId);
    }

    private static UUID requireId(UUID value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }
}
