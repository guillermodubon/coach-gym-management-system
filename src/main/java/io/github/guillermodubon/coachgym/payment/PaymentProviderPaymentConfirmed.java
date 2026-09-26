package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a verified provider event creates a payment. */
public record PaymentProviderPaymentConfirmed(
        UUID paymentAttemptId,
        UUID paymentId,
        PaymentProvider provider,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        UUID branchId) {

    public PaymentProviderPaymentConfirmed(UUID paymentAttemptId, UUID paymentId,
            PaymentProvider provider, BigDecimal amount, String currency,
            Instant occurredAt) {
        this(paymentAttemptId, paymentId, provider, amount, currency, occurredAt, null);
    }

    public PaymentProviderPaymentConfirmed {
        if (paymentAttemptId == null || paymentId == null || provider == null
                || amount == null || amount.signum() <= 0 || currency == null
                || !currency.matches("[A-Z]{3}") || occurredAt == null) {
            throw new IllegalArgumentException("Complete provider payment confirmation is required.");
        }
    }
}
