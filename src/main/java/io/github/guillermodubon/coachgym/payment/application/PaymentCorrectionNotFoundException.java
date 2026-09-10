package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates that the requested payment does not exist. */
public class PaymentCorrectionNotFoundException extends RuntimeException {

    private final UUID paymentId;

    public PaymentCorrectionNotFoundException(UUID paymentId) {
        super("Payment was not found.");
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
