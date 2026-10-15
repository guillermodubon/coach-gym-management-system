package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.time.Instant;

/** Command that closes a reserved provider-event identity. */
public record FinalizePaymentProviderEventCommand(
        PaymentProvider provider,
        String providerEventReference,
        PaymentProviderEventProcessingResult processingResult,
        Instant processedAt) {

    public FinalizePaymentProviderEventCommand {
        if (provider == null || processingResult == null || processedAt == null
                || processingResult == PaymentProviderEventProcessingResult.PENDING) {
            throw new IllegalArgumentException("Complete provider event finalization data is required.");
        }
        if (providerEventReference == null || providerEventReference.isBlank()) {
            throw new IllegalArgumentException("Provider event reference is required.");
        }
        providerEventReference = providerEventReference.strip();
    }
}
