package io.github.guillermodubon.coachgym.user;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Organization-administrator request to change another account's role and scope. */
public record ChangeStaffRoleScopeCommand(
        UUID targetUserId,
        Set<RoleCode> requestedRoles,
        StaffScopeType requestedScope,
        String reason,
        long expectedVersion) {

    public ChangeStaffRoleScopeCommand {
        if (targetUserId == null) {
            throw new StaffIdentityValidationException("Target staff identity is required.");
        }
        Objects.requireNonNull(requestedRoles, "Requested roles are required.");
        if (requestedRoles.isEmpty()) {
            throw new StaffIdentityValidationException("At least one supported role is required.");
        }
        requestedRoles = Set.copyOf(EnumSet.copyOf(requestedRoles));
        requestedScope = Objects.requireNonNull(requestedScope, "Requested scope is required.");
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(requestedRoles, requestedScope);
        reason = StaffIdentityValuePolicy.requireReason(reason);
        if (expectedVersion < 0) {
            throw new StaffIdentityValidationException("Expected account version must not be negative.");
        }
    }

    @Override
    public String toString() {
        return "ChangeStaffRoleScopeCommand[requestedRoles=" + requestedRoles
                + ", requestedScope=" + requestedScope
                + ", reasonPresent=" + (reason != null)
                + ", expectedVersion=" + expectedVersion
                + ']';
    }
}
