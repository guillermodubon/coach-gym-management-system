package io.github.guillermodubon.coachgym.user;

import java.util.Set;
import java.util.UUID;

/** Minimal public projection of an active staff account. */
public record ActiveStaffMember(
        UUID userId,
        String username,
        Set<RoleCode> roles) {

    public ActiveStaffMember {
        if (userId == null) {
            throw new IllegalArgumentException("Active staff user id is required.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Active staff username is required.");
        }
        username = username.strip();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
