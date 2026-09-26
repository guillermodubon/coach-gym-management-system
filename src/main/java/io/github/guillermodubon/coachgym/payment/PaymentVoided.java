package io.github.guillermodubon.coachgym.payment;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a payment is successfully voided. */
public record PaymentVoided(
        UUID paymentId,
        String paymentCode,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        UUID changedByUserId,
        String actorIdentifier,
        Instant occurredAt,
        UUID branchId) {

    public PaymentVoided(UUID paymentId, String paymentCode,
            PaymentStatus previousStatus, PaymentStatus newStatus,
            UUID changedByUserId, String actorIdentifier, Instant occurredAt) {
        this(paymentId, paymentCode, previousStatus, newStatus, changedByUserId,
                actorIdentifier, occurredAt, null);
    }

    public PaymentVoided {
        if (paymentId == null) throw new IllegalArgumentException("Payment id is required.");
        if (paymentCode == null || paymentCode.isBlank()) throw new IllegalArgumentException("Payment code is required.");
        paymentCode = paymentCode.strip();
        if (previousStatus != PaymentStatus.PAID || newStatus != PaymentStatus.VOIDED) {
            throw new IllegalArgumentException("Payment void event requires PAID to VOIDED.");
        }
        if (changedByUserId == null) throw new IllegalArgumentException("Payment correction actor is required.");
        if (actorIdentifier == null || actorIdentifier.isBlank()) throw new IllegalArgumentException("Actor identifier is required.");
        actorIdentifier = actorIdentifier.strip();
        if (occurredAt == null) throw new IllegalArgumentException("Payment correction timestamp is required.");
    }
}
