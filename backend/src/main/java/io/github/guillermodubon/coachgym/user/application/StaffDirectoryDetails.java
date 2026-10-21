package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Administrative detail projection; credentials and security telemetry are excluded. */
public record StaffDirectoryDetails(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        StaffAccountStatus status,
        List<RoleCode> roles,
        StaffScopeType scope,
        long version,
        long scopeVersion,
        String email,
        Instant createdAt,
        Instant updatedAt) {

    public StaffDirectoryDetails {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(firstName, "firstName is required");
        Objects.requireNonNull(lastName, "lastName is required");
        Objects.requireNonNull(status, "status is required");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles is required"));
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (roles.isEmpty() || version < 0 || scopeVersion < 0) {
            throw new IllegalArgumentException("Directory detail values are invalid.");
        }
    }

    public StaffDirectoryEntry entry() {
        return new StaffDirectoryEntry(
                userId, username, firstName, lastName, status, roles,
                scope, version, scopeVersion);
    }
}
