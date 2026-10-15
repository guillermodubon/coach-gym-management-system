package io.github.guillermodubon.coachgym.user;

/** Raised when a staff identity operation is outside the actor's authority. */
public final class StaffIdentityAuthorizationException extends SecurityException {

    public StaffIdentityAuthorizationException(String message) {
        super(message);
    }
}
