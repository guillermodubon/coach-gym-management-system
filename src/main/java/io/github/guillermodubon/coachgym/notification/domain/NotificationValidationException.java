package io.github.guillermodubon.coachgym.notification.domain;

/** Raised when an internal notification violates a domain invariant. */
public class NotificationValidationException extends RuntimeException {

    public NotificationValidationException(String message) {
        super(message);
    }
}
