package io.github.guillermodubon.coachgym.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Provider-confirmed payment write command; it cannot select a non-card method. */
public record ProviderConfirmedPaymentCommand(
        UUID paymentId,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        BigDecimal amount,
        String currency,
        UUID registeredByUserId,
        Instant paidAt,
        Instant occurredAt) {

    public ProviderConfirmedPaymentCommand {
        if (paymentId == null || clientId == null || membershipId == null
                || membershipPeriodId == null || registeredByUserId == null
                || amount == null || amount.signum() <= 0 || currency == null
                || currency.isBlank() || paidAt == null || occurredAt == null) {
            throw new IllegalArgumentException("Complete provider-confirmed payment data is required.");
        }
        currency = currency.strip().toUpperCase(java.util.Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Payment currency must be a three-letter code.");
        }
    }
}
