package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Indicates that a staff scope update used a stale version. */
public class StaffScopeVersionConflictException extends RuntimeException {

    public StaffScopeVersionConflictException(UUID userId) {
        super("The staff scope was modified by another operation. Reload it and try again.");
    }
}
