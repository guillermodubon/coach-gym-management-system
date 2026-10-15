package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Indicates that another backend instance currently owns the delivery lease. */
public class EmailDeliveryClaimConflictException extends RuntimeException {

    private final UUID deliveryId;

    public EmailDeliveryClaimConflictException(UUID deliveryId) {
        super("Email delivery is already being processed.");
        this.deliveryId = deliveryId;
    }

    public UUID deliveryId() {
        return deliveryId;
    }
}
