package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Server-owned values for the first durable state of a payment attempt. */
public record PersistPaymentAttemptCommand(
        UUID paymentAttemptId,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        PaymentProvider provider,
        BigDecimal expectedAmount,
        String currency,
        UUID createdByUserId,
        Instant occurredAt,
        UUID initiatedAtBranchId) {

    public PersistPaymentAttemptCommand(
            UUID paymentAttemptId,
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            PaymentProvider provider,
            BigDecimal expectedAmount,
            String currency,
            UUID createdByUserId,
            Instant occurredAt) {
        this(paymentAttemptId, clientId, membershipId, membershipPeriodId, provider,
                expectedAmount, currency, createdByUserId, occurredAt, null);
    }
}
