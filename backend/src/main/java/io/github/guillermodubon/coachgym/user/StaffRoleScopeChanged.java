package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Privacy-minimized privilege-change fact; reasons and credentials are never published. */
public record StaffRoleScopeChanged(
        UUID targetUserId,
        Set<RoleCode> previousRoles,
        Set<RoleCode> newRoles,
        StaffScopeType previousScope,
        StaffScopeType newScope,
        UUID actorUserId,
        Instant occurredAt,
        boolean reasonPresent) {

    public StaffRoleScopeChanged {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        previousRoles = normalized(previousRoles);
        newRoles = normalized(newRoles);
        previousScope = Objects.requireNonNull(previousScope);
        newScope = Objects.requireNonNull(newScope);
        actorUserId = StaffAssignmentValuePolicy.requireId(actorUserId, "actorUserId");
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
        if (targetUserId.equals(actorUserId)
                || (previousRoles.equals(newRoles) && previousScope == newScope)) {
            throw new StaffIdentityValidationException("Privilege-change event data is invalid.");
        }
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(previousRoles, previousScope);
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(newRoles, newScope);
    }

    private static Set<RoleCode> normalized(Set<RoleCode> roles) {
        Objects.requireNonNull(roles);
        if (roles.isEmpty()) {
            throw new StaffIdentityValidationException("At least one supported role is required.");
        }
        return Set.copyOf(EnumSet.copyOf(roles));
    }
}
