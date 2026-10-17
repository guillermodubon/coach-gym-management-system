package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal, credential-free snapshot used by staff account administration. */
public record StaffIdentityAdministrationState(
        UUID userId,
        StaffAccountStatus accountStatus,
        Set<RoleCode> roles,
        StaffScopeType scopeType,
        long securityVersion,
        long scopeVersion) {

    public StaffIdentityAdministrationState {
        if (userId == null) {
            throw new IllegalArgumentException("Staff identity is required.");
        }
        accountStatus = Objects.requireNonNull(accountStatus);
        Objects.requireNonNull(roles);
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
        scopeType = Objects.requireNonNull(scopeType);
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(roles, scopeType);
        if (securityVersion < 0 || scopeVersion < 0) {
            throw new IllegalArgumentException("Staff identity versions must not be negative.");
        }
    }
}
