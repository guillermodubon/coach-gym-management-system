package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Administrative request to void an incorrectly registered confirmed payment. */
public record VoidPaymentCommand(
        UUID paymentId,
        String reason,
        long expectedVersion) {

    public VoidPaymentCommand {
        paymentId = PaymentCorrectionCommandValidation.paymentId(paymentId);
        reason = PaymentCorrectionCommandValidation.reason(reason);
        PaymentCorrectionCommandValidation.expectedVersion(expectedVersion);
    }
}
