package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Privacy-safe event emitted after a full refund is successfully recorded. */
public record PaymentRefunded(
        UUID paymentId,
        String paymentCode,
        UUID refundId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        BigDecimal amount,
        String currency,
        boolean externalReferencePresent,
        UUID changedByUserId,
        String actorIdentifier,
        Instant occurredAt,
        UUID branchId) {

    public PaymentRefunded(UUID paymentId, String paymentCode, UUID refundId,
            PaymentStatus previousStatus, PaymentStatus newStatus,
            BigDecimal amount, String currency, boolean externalReferencePresent,
            UUID changedByUserId, String actorIdentifier, Instant occurredAt) {
        this(paymentId, paymentCode, refundId, previousStatus, newStatus, amount,
                currency, externalReferencePresent, changedByUserId,
                actorIdentifier, occurredAt, null);
    }

    public PaymentRefunded {
        if (paymentId == null || refundId == null) throw new IllegalArgumentException("Payment and refund ids are required.");
        if (paymentCode == null || paymentCode.isBlank()) throw new IllegalArgumentException("Payment code is required.");
        paymentCode = paymentCode.strip();
        if (previousStatus != PaymentStatus.PAID || newStatus != PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("Payment refund event requires PAID to REFUNDED.");
        }
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Refund amount must be positive.");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("Refund currency is required.");
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Refund currency must be a three-letter code.");
        if (changedByUserId == null) throw new IllegalArgumentException("Payment correction actor is required.");
        if (actorIdentifier == null || actorIdentifier.isBlank()) throw new IllegalArgumentException("Actor identifier is required.");
        actorIdentifier = actorIdentifier.strip();
        if (occurredAt == null) throw new IllegalArgumentException("Payment correction timestamp is required.");
    }
}
