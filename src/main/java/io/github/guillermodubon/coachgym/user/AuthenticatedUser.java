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
        Set<RoleCode> roles) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }
}
