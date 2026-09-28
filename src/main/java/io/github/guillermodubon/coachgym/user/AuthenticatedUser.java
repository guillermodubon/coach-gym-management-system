package io.github.guillermodubon.coachgym.user;

import java.util.Set;
import java.util.UUID;

/**
 * Security-facing representation of an active internal user.
 */
public record AuthenticatedUser(
        UUID id,
        String username,
        String passwordHash,
        String fullName,
        Set<RoleCode> roles,
        long securityVersion,
        boolean passwordChangeRequired) {

    public AuthenticatedUser(
            UUID id,
            String username,
            String passwordHash,
            String fullName,
            Set<RoleCode> roles) {
        this(id, username, passwordHash, fullName, roles, 0, false);
    }

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        if (securityVersion < 0) {
            throw new IllegalArgumentException("Security version must not be negative.");
        }
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id
                + ", roles=" + roles
                + ", securityVersion=" + securityVersion
                + ", passwordChangeRequired=" + passwordChangeRequired
                + ']';
    }
}
