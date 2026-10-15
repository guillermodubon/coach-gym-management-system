package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;

/** Indicates that a payment state is not eligible for canonical receipt generation. */
public class PaymentReceiptStateConflictException extends RuntimeException {

    private final UUID paymentId;
    private final PaymentStatus currentStatus;

    public PaymentReceiptStateConflictException(
            UUID paymentId,
            PaymentStatus currentStatus) {
        super("A receipt can only be generated for a confirmed paid payment.");
        this.paymentId = paymentId;
        this.currentStatus = currentStatus;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentStatus currentStatus() {
        return currentStatus;
    }
}
