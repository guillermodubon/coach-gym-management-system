package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates that a payment attempt does not exist. */
public class PaymentAttemptNotFoundException extends RuntimeException {

    private final UUID paymentAttemptId;

    public PaymentAttemptNotFoundException(UUID paymentAttemptId) {
        super("Payment attempt was not found.");
        this.paymentAttemptId = paymentAttemptId;
    }

    public UUID paymentAttemptId() {
        return paymentAttemptId;
    }
}
