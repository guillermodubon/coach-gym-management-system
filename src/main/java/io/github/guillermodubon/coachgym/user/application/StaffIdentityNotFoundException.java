package io.github.guillermodubon.coachgym.user.application;

/** Raised when an administrative command addresses no provisioned staff account. */
public final class StaffIdentityNotFoundException extends RuntimeException {

    public StaffIdentityNotFoundException() {
        super("Staff identity was not found.");
    }
}
