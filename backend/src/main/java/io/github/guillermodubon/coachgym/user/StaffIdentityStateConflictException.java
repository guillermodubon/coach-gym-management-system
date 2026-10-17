package io.github.guillermodubon.coachgym.user;

/** Raised when a staff identity lifecycle transition is not permitted. */
public final class StaffIdentityStateConflictException extends IllegalStateException {

    public StaffIdentityStateConflictException(String message) {
        super(message);
    }
}
