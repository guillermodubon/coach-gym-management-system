package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;

/** Indicates that the requested correction is incompatible with payment state. */
public class PaymentCorrectionStateConflictException extends RuntimeException {

    private final UUID paymentId;
    private final PaymentStatus currentStatus;
    private final PaymentStatus requestedStatus;

    public PaymentCorrectionStateConflictException(
            UUID paymentId,
            PaymentStatus currentStatus,
            PaymentStatus requestedStatus) {
        super("Payment correction is not allowed from the current state.");
        this.paymentId = paymentId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentStatus currentStatus() {
        return currentStatus;
    }

    public PaymentStatus requestedStatus() {
        return requestedStatus;
    }
}
