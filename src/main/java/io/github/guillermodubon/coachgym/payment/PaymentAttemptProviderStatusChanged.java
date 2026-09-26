package io.github.guillermodubon.coachgym.payment;

import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptTransitionPolicy;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted for a provider-originated attempt outcome. */
public record PaymentAttemptProviderStatusChanged(
        UUID paymentAttemptId,
        PaymentProvider provider,
        PaymentAttemptStatus previousStatus,
        PaymentAttemptStatus currentStatus,
        PaymentAttemptFailureCode failureCode,
        UUID confirmedPaymentId,
        boolean providerEventReferencePresent,
        Instant occurredAt,
        UUID branchId) {

    public PaymentAttemptProviderStatusChanged(UUID paymentAttemptId,
            PaymentProvider provider, PaymentAttemptStatus previousStatus,
            PaymentAttemptStatus currentStatus, PaymentAttemptFailureCode failureCode,
            UUID confirmedPaymentId, boolean providerEventReferencePresent,
            Instant occurredAt) {
        this(paymentAttemptId, provider, previousStatus, currentStatus, failureCode,
                confirmedPaymentId, providerEventReferencePresent, occurredAt, null);
    }

    public PaymentAttemptProviderStatusChanged {
        if (paymentAttemptId == null || provider == null || previousStatus == null
                || currentStatus == null || occurredAt == null) {
            throw new IllegalArgumentException("Complete provider attempt transition is required.");
        }
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                paymentAttemptId, previousStatus, currentStatus);
        PaymentAttemptTransitionPolicy.requireOutcomeConsistency(
                currentStatus, failureCode, confirmedPaymentId);
    }
}
