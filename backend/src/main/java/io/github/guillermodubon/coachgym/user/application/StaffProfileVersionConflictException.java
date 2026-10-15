package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Raised when a profile mutation uses an obsolete optimistic-lock version. */
public class StaffProfileVersionConflictException extends RuntimeException {

    private final UUID userId;

    public StaffProfileVersionConflictException(UUID userId) {
        super("Staff profile version is stale.");
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
