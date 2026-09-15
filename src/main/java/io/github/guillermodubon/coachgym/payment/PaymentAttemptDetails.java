package io.github.guillermodubon.coachgym.payment;

import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptTransitionPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Safe payment-attempt projection. Provider reference values and checkout URLs
 * intentionally remain outside this public contract.
 */
public record PaymentAttemptDetails(
        UUID id,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        PaymentProvider provider,
        PaymentAttemptStatus status,
        BigDecimal expectedAmount,
        String currency,
        PaymentAttemptFailureCode failureCode,
        UUID confirmedPaymentId,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version) {

    public PaymentAttemptDetails {
        requireIdentifier(id, "Payment attempt id");
        requireIdentifier(clientId, "Client id");
        requireIdentifier(membershipId, "Membership id");
        requireIdentifier(membershipPeriodId, "Membership period id");
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Payment attempt status is required.");
        }
        if (expectedAmount == null || expectedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Expected amount must be positive.");
        }
        currency = normalizeCurrency(currency);
        requireIdentifier(createdByUserId, "Payment attempt creator id");
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamps are required.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Payment attempt version must not be negative.");
        }
        PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                status, failureCode, confirmedPaymentId, completedAt);
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
