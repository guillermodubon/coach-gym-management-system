package io.github.guillermodubon.coachgym.user;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable authorization facts consumed by future branch-aware use cases. */
public record StaffAuthorizationContext(
        UUID userId,
        Set<RoleCode> roles,
        StaffAccountStatus accountStatus,
        StaffScopeType scopeType,
        Set<UUID> assignedBranchIds) {

    public StaffAuthorizationContext {
        userId = StaffAssignmentValuePolicy.requireId(userId, "userId");
        Objects.requireNonNull(roles, "roles is required");
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
        accountStatus = Objects.requireNonNull(accountStatus, "accountStatus is required");
        scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
        Objects.requireNonNull(assignedBranchIds, "assignedBranchIds is required");
        assignedBranchIds = Set.copyOf(assignedBranchIds);
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(roles, scopeType);
        if (accountStatus != StaffAccountStatus.ACTIVE && !assignedBranchIds.isEmpty()) {
            throw new StaffScopeValidationException("inactive staff cannot have branch assignments");
        }
    }

    public boolean organizationAdmin() {
        return accountStatus == StaffAccountStatus.ACTIVE
                && scopeType == StaffScopeType.ORGANIZATION
                && roles.contains(RoleCode.ADMIN);
    }

    public boolean branchAdmin() {
        return accountStatus == StaffAccountStatus.ACTIVE
                && scopeType == StaffScopeType.BRANCH
                && roles.contains(RoleCode.ADMIN);
    }

    public boolean receptionist() {
        return accountStatus == StaffAccountStatus.ACTIVE
                && scopeType == StaffScopeType.BRANCH
                && roles.contains(RoleCode.RECEPTIONIST);
    }

    public boolean assignedTo(UUID branchId) {
        return branchId != null && assignedBranchIds.contains(branchId);
    }
}
