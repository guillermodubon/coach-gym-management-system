package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Raised when a requested persisted delivery does not exist. */
public class EmailDeliveryNotFoundException extends RuntimeException {

    private final UUID deliveryId;

    public EmailDeliveryNotFoundException(UUID deliveryId) {
        super("Email delivery could not be found.");
        this.deliveryId = deliveryId;
    }

    public UUID deliveryId() {
        return deliveryId;
    }
}
