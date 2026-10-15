package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable details of a full refund associated with one payment. */
public record PaymentRefundDetails(
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String reason,
        String externalReference,
        Instant refundedAt,
        UUID refundedByUserId) {

    public PaymentRefundDetails {
        if (refundId == null) {
            throw new IllegalArgumentException("Refund id is required.");
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        amount = PaymentCorrectionValidation.positiveAmount(amount);
        currency = PaymentCorrectionValidation.currency(currency);
        reason = PaymentCorrectionValidation.requiredText(
                reason, "Refund reason");
        externalReference = PaymentCorrectionValidation.optionalText(
                externalReference);
        if (refundedAt == null) {
            throw new IllegalArgumentException("Refund timestamp is required.");
        }
        if (refundedByUserId == null) {
            throw new IllegalArgumentException("Refund actor is required.");
        }
    }
}
