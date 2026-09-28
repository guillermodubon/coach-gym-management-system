package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Raised when an account/security version supplied by an administrator is stale. */
public final class StaffIdentityVersionConflictException extends RuntimeException {

    private final UUID userId;

    public StaffIdentityVersionConflictException(UUID userId) {
        super("Staff identity changed since it was read.");
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
