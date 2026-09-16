package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Indicates that no further manual retries are allowed for a delivery. */
public class EmailDeliveryRetryLimitExceededException extends RuntimeException {

    private final UUID deliveryId;
    private final int attemptCount;
    private final int maximumRetryCount;

    public EmailDeliveryRetryLimitExceededException(
            UUID deliveryId, int attemptCount, int maximumRetryCount) {
        super("Email delivery retry limit has been reached.");
        this.deliveryId = deliveryId;
        this.attemptCount = attemptCount;
        this.maximumRetryCount = maximumRetryCount;
    }

    public UUID deliveryId() {
        return deliveryId;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maximumRetryCount() {
        return maximumRetryCount;
    }
}
