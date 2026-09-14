package io.github.guillermodubon.coachgym.access.application;

/** Safe application failure for access-attempt persistence or reads. */
public final class AccessRecordDataAccessException extends RuntimeException {

    public AccessRecordDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
