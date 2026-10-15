package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates that another transaction already created the canonical receipt. */
public class PaymentReceiptDuplicateException extends RuntimeException {

    private final UUID paymentId;

    public PaymentReceiptDuplicateException(UUID paymentId) {
        super("A canonical receipt already exists for this payment.");
        if (paymentId == null) {
            throw new PaymentReceiptValidationException("Payment id is required.");
        }
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
