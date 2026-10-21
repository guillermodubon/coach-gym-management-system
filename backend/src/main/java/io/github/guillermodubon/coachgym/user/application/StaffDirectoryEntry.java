package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Privacy-limited staff row for directory results. */
public record StaffDirectoryEntry(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        StaffAccountStatus status,
        List<RoleCode> roles,
        StaffScopeType scope,
        long version,
        long scopeVersion) {

    public StaffDirectoryEntry {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(firstName, "firstName is required");
        Objects.requireNonNull(lastName, "lastName is required");
        Objects.requireNonNull(status, "status is required");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles is required"));
        Objects.requireNonNull(scope, "scope is required");
        if (roles.isEmpty() || version < 0 || scopeVersion < 0) {
            throw new IllegalArgumentException("Directory entry values are invalid.");
        }
    }
}
