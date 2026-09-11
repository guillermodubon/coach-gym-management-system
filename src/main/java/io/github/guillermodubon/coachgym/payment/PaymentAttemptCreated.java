package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Privacy-safe event emitted after a payment attempt is created. */
public record PaymentAttemptCreated(
        UUID paymentAttemptId,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        PaymentProvider provider,
        BigDecimal expectedAmount,
        String currency,
        UUID createdByUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public PaymentAttemptCreated {
        requireIdentifier(paymentAttemptId, "Payment attempt id");
        requireIdentifier(clientId, "Client id");
        requireIdentifier(membershipId, "Membership id");
        requireIdentifier(membershipPeriodId, "Membership period id");
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        if (expectedAmount == null || expectedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Expected amount must be positive.");
        }
        currency = normalizeCurrency(currency);
        requireIdentifier(createdByUserId, "Payment attempt creator id");
        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException("Actor identifier is required.");
        }
        actorIdentifier = actorIdentifier.strip();
        if (occurredAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamp is required.");
        }
    }

    private static void requireIdentifier(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment attempt currency is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Payment attempt currency must be a three-letter code.");
        }
        return normalized;
    }
}
