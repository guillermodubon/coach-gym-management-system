package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Public immutable representation of one staff account's current scope. */
public record StaffScopeDetails(
        UUID userId,
        Set<RoleCode> roles,
        StaffScopeType scopeType,
        Instant grantedAt,
        UUID grantedByUserId,
        long version) {

    public StaffScopeDetails {
        userId = StaffAssignmentValuePolicy.requireId(userId, "userId");
        Objects.requireNonNull(roles, "roles is required");
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
        scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
        grantedAt = StaffAssignmentValuePolicy.requireInstant(grantedAt, "grantedAt");
        if (grantedByUserId != null && grantedByUserId.equals(userId)) {
            throw new StaffScopeValidationException("a staff member cannot grant their own scope");
        }
        StaffAssignmentValuePolicy.requireVersion(version, "version");
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(roles, scopeType);
    }
}
