package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentRefundDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PaymentRefundResponse(
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String reason,
        String externalReference,
        Instant refundedAt,
        UUID refundedByUserId) {

    static PaymentRefundResponse from(PaymentRefundDetails details) {
        if (details == null) {
            return null;
        }
        return new PaymentRefundResponse(
                details.refundId(),
                details.paymentId(),
                details.amount(),
                details.currency(),
                details.reason(),
                details.externalReference(),
                details.refundedAt(),
                details.refundedByUserId());
    }
}
