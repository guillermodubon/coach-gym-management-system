package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Safe result of a password change; credentials are intentionally absent. */
public record StaffPasswordChangeResult(
        UUID userId,
        long profileVersion,
        boolean reauthenticationRequired) {

    public StaffPasswordChangeResult {
        if (userId == null) {
            throw new StaffProfileValidationException("Staff user id is required.");
        }
        if (profileVersion < 0) {
            throw new StaffProfileValidationException(
                    "Staff profile version must not be negative.");
        }
    }
}
