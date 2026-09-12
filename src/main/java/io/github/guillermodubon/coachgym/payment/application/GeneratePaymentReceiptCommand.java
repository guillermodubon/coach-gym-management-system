package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Server-authorized request to generate the canonical receipt for a payment. */
public record GeneratePaymentReceiptCommand(UUID paymentId) {

    public GeneratePaymentReceiptCommand {
        if (paymentId == null) {
            throw new PaymentReceiptValidationException("Payment id is required.");
        }
    }
}
