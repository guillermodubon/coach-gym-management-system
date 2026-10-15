package io.github.guillermodubon.coachgym.payment;

import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptTransitionPolicy;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a payment-attempt state transition. */
public record PaymentAttemptStatusChanged(
        UUID paymentAttemptId,
        PaymentProvider provider,
        PaymentAttemptStatus previousStatus,
        PaymentAttemptStatus currentStatus,
        PaymentAttemptFailureCode failureCode,
        UUID confirmedPaymentId,
        UUID initiatedByUserId,
        Instant occurredAt,
        UUID branchId) {

    public PaymentAttemptStatusChanged(UUID paymentAttemptId, PaymentProvider provider,
            PaymentAttemptStatus previousStatus, PaymentAttemptStatus currentStatus,
            PaymentAttemptFailureCode failureCode, UUID confirmedPaymentId,
            UUID initiatedByUserId, Instant occurredAt) {
        this(paymentAttemptId, provider, previousStatus, currentStatus, failureCode,
                confirmedPaymentId, initiatedByUserId, occurredAt, null);
    }

    public PaymentAttemptStatusChanged {
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        if (provider == null || previousStatus == null || currentStatus == null) {
            throw new IllegalArgumentException("Payment attempt transition is required.");
        }
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                paymentAttemptId, previousStatus, currentStatus);
        PaymentAttemptTransitionPolicy.requireOutcomeConsistency(
                currentStatus, failureCode, confirmedPaymentId);
        if (initiatedByUserId == null) {
            throw new IllegalArgumentException("Payment attempt creator id is required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamp is required.");
        }
    }
}
