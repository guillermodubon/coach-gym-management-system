package io.github.guillermodubon.coachgym.user.application;

/** Wraps an unexpected failure while reading or updating staff profile data. */
public class StaffProfileDataAccessException extends RuntimeException {

    public StaffProfileDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
