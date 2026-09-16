package io.github.guillermodubon.coachgym.notification.application;

/** Raised when a logical delivery or immutable attempt already exists. */
public class EmailDeliveryDuplicateException extends RuntimeException {

    public EmailDeliveryDuplicateException() {
        super("Email delivery already exists.");
    }
}
