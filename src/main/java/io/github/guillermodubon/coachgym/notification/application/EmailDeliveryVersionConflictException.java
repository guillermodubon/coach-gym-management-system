package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Indicates that a delivery changed after the caller read its version. */
public class EmailDeliveryVersionConflictException extends RuntimeException {

    private final UUID deliveryId;
    private final long expectedVersion;
    private final long currentVersion;

    public EmailDeliveryVersionConflictException(
            UUID deliveryId, long expectedVersion, long currentVersion) {
        super("Email delivery was modified by another operation.");
        this.deliveryId = deliveryId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID deliveryId() {
        return deliveryId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
