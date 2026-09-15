package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;

/** Provider-neutral request to cancel or expire a hosted checkout. */
public record ProviderCheckoutCancellationRequest(
        PaymentProvider provider,
        String checkoutReference) {

    public ProviderCheckoutCancellationRequest {
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        if (checkoutReference == null || checkoutReference.isBlank()) {
            throw new IllegalArgumentException("Checkout reference is required.");
        }
        checkoutReference = checkoutReference.strip();
    }
}
