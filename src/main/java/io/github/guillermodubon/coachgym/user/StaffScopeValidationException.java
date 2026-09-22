package io.github.guillermodubon.coachgym.user;

/** Raised when a staff scope value is invalid or incompatible with its roles. */
public final class StaffScopeValidationException extends IllegalArgumentException {

    public StaffScopeValidationException(String message) {
        super(message);
    }
}
