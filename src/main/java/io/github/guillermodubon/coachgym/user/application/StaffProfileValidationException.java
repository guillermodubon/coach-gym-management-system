package io.github.guillermodubon.coachgym.user.application;

/** Indicates invalid self-profile contract data. */
public class StaffProfileValidationException extends RuntimeException {

    public StaffProfileValidationException(String message) {
        super(message);
    }

    public StaffProfileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
