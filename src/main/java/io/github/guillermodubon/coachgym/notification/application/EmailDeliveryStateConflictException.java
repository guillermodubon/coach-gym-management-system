package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import java.util.UUID;

/** Indicates that a delivery cannot transition from its current state. */
public class EmailDeliveryStateConflictException extends RuntimeException {

    private final UUID deliveryId;
    private final EmailDeliveryStatus currentStatus;
    private final EmailDeliveryStatus requestedStatus;

    public EmailDeliveryStateConflictException(
            UUID deliveryId,
            EmailDeliveryStatus currentStatus,
            EmailDeliveryStatus requestedStatus) {
        super("Email delivery transition is not allowed from the current state.");
        this.deliveryId = deliveryId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID deliveryId() {
        return deliveryId;
    }

    public EmailDeliveryStatus currentStatus() {
        return currentStatus;
    }

    public EmailDeliveryStatus requestedStatus() {
        return requestedStatus;
    }
}
