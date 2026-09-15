package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates that a full refund has already been registered for a payment. */
public class PaymentRefundConflictException extends RuntimeException {

    private final UUID paymentId;

    public PaymentRefundConflictException(UUID paymentId) {
        super("Payment already has a registered refund.");
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
