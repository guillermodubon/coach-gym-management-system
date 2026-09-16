package io.github.guillermodubon.coachgym.notification.application;

/** Safe boundary exception for delivery persistence failures. */
public class EmailDeliveryDataAccessException extends RuntimeException {

    public EmailDeliveryDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
