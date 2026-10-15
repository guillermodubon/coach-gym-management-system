package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates an optimistic-lock conflict while correcting a payment. */
public class PaymentCorrectionVersionConflictException extends RuntimeException {

    private final UUID paymentId;
    private final long expectedVersion;
    private final long currentVersion;

    public PaymentCorrectionVersionConflictException(
            UUID paymentId,
            long expectedVersion,
            long currentVersion) {
        super("Payment was modified by another operation.");
        this.paymentId = paymentId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
