package io.github.guillermodubon.coachgym.notification.domain;

/** Raised when a transactional-email contract violates its safe bounds. */
public class EmailDeliveryValidationException extends RuntimeException {

    public EmailDeliveryValidationException(String message) {
        super(message);
    }
}
