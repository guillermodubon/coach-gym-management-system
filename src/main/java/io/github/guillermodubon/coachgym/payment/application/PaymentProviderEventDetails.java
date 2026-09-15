package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

/** Safe projection of a provider-event idempotency record. */
public record PaymentProviderEventDetails(
        UUID id,
        PaymentProvider provider,
        String providerEventReference,
        PaymentProviderEventType eventType,
        UUID paymentAttemptId,
        PaymentProviderEventProcessingResult processingResult,
        Instant receivedAt,
        Instant processedAt) {

    public PaymentProviderEventDetails {
        if (id == null || provider == null || eventType == null
                || processingResult == null || receivedAt == null) {
            throw new IllegalArgumentException("Complete provider event details are required.");
        }
        if (providerEventReference == null || providerEventReference.isBlank()) {
            throw new IllegalArgumentException("Provider event reference is required.");
        }
        providerEventReference = providerEventReference.strip();
        if (processingResult == PaymentProviderEventProcessingResult.PENDING
                ? processedAt != null
                : processedAt == null) {
            throw new IllegalArgumentException(
                    "Provider event processing timestamp does not match its result.");
        }
    }
}
