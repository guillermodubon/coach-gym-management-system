package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Administrative request to record a full refund of a confirmed payment. */
public record RefundPaymentCommand(
        UUID paymentId,
        String reason,
        String externalReference,
        long expectedVersion) {

    public RefundPaymentCommand {
        paymentId = PaymentCorrectionCommandValidation.paymentId(paymentId);
        reason = PaymentCorrectionCommandValidation.reason(reason);
        externalReference = PaymentCorrectionCommandValidation.externalReference(
                externalReference);
        PaymentCorrectionCommandValidation.expectedVersion(expectedVersion);
    }
}
