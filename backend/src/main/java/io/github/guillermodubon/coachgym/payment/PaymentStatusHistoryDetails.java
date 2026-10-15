package io.github.guillermodubon.coachgym.payment;

import java.time.Instant;
import java.util.UUID;

/** Immutable append-only payment status transition entry. */
public record PaymentStatusHistoryDetails(
        UUID historyId,
        UUID paymentId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        String reason,
        Instant changedAt,
        UUID changedByUserId) {

    public PaymentStatusHistoryDetails {
        if (historyId == null) {
            throw new IllegalArgumentException("Payment history id is required.");
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        if (previousStatus == null || newStatus == null) {
            throw new IllegalArgumentException(
                    "Payment transition statuses are required.");
        }
        if (previousStatus == newStatus) {
            throw new IllegalArgumentException(
                    "Payment transition statuses must be different.");
        }
        reason = PaymentCorrectionValidation.requiredText(
                reason, "Payment correction reason");
        if (changedAt == null) {
            throw new IllegalArgumentException(
                    "Payment status change timestamp is required.");
        }
        if (changedByUserId == null) {
            throw new IllegalArgumentException(
                    "Payment status change actor is required.");
        }
    }
}
