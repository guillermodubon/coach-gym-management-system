package io.github.guillermodubon.coachgym.payment.application;

import java.time.Instant;
import java.util.UUID;

/** Atomic provider-success transition command for a payment attempt. */
public record ConfirmProviderPaymentAttemptCommand(
        UUID paymentAttemptId,
        long expectedVersion,
        String providerPaymentReference,
        UUID confirmedPaymentId,
        Instant occurredAt) {

    public ConfirmProviderPaymentAttemptCommand {
        if (paymentAttemptId == null || expectedVersion < 0 || confirmedPaymentId == null
                || occurredAt == null || providerPaymentReference == null
                || providerPaymentReference.isBlank()) {
            throw new IllegalArgumentException("Complete provider success data is required.");
        }
        providerPaymentReference = providerPaymentReference.strip();
    }
}
