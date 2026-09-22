package io.github.guillermodubon.coachgym.user.application;

/** Safe boundary exception for unexpected staff-scope persistence failures. */
public class StaffScopeDataAccessException extends RuntimeException {

    public StaffScopeDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
