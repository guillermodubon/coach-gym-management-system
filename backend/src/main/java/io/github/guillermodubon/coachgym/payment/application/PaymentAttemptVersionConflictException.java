package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates an optimistic-lock conflict while changing a payment attempt. */
public class PaymentAttemptVersionConflictException extends RuntimeException {

    private final UUID paymentAttemptId;
    private final long expectedVersion;
    private final long currentVersion;

    public PaymentAttemptVersionConflictException(
            UUID paymentAttemptId,
            long expectedVersion,
            long currentVersion) {
        super("Payment attempt was modified by another operation.");
        this.paymentAttemptId = paymentAttemptId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID paymentAttemptId() {
        return paymentAttemptId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
