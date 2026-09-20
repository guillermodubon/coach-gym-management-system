package io.github.guillermodubon.coachgym.user.application;

/** Raised when the supplied current password does not authenticate the actor. */
public class StaffCurrentPasswordInvalidException extends RuntimeException {

    public StaffCurrentPasswordInvalidException() {
        super("The current password is invalid.");
    }
}
