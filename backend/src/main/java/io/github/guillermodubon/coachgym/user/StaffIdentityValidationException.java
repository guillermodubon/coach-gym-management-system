package io.github.guillermodubon.coachgym.user;

/** Raised when a staff identity contract contains invalid values. */
public final class StaffIdentityValidationException extends IllegalArgumentException {

    public StaffIdentityValidationException(String message) {
        super(message);
    }
}
