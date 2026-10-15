package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Safe failure raised when authoritative client email is missing or invalid. */
public class EmailDeliveryRecipientUnavailableException extends RuntimeException {

    private final UUID clientId;

    public EmailDeliveryRecipientUnavailableException(UUID clientId) {
        super("Email delivery recipient is unavailable.");
        this.clientId = clientId;
    }

    public UUID clientId() {
        return clientId;
    }
}
