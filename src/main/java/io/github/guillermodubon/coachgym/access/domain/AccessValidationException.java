package io.github.guillermodubon.coachgym.access.domain;

/**
 * Thrown when an access check-in request fails structural validation
 * before any business resolution is attempted.
 *
 * <p>Maps to HTTP 400 in the controller. Does not produce an
 * {@code access_records} row, an event, or an audit entry.</p>
 */
public class AccessValidationException extends RuntimeException {

    public AccessValidationException(String message) {
        super(message);
    }
}
