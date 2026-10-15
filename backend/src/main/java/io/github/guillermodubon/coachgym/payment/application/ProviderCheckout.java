package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.net.URI;
import java.time.Instant;

/**
 * Provider checkout result used inside the payment module. Its URL must never
 * be published in events, audit metadata, or logs.
 */
public record ProviderCheckout(
        PaymentProvider provider,
        String checkoutReference,
        URI checkoutUrl,
        Instant expiresAt) {

    public ProviderCheckout {
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        if (checkoutReference == null || checkoutReference.isBlank()) {
            throw new IllegalArgumentException("Checkout reference is required.");
        }
        checkoutReference = checkoutReference.strip();
        if (checkoutUrl == null || !checkoutUrl.isAbsolute()) {
            throw new IllegalArgumentException("Checkout URL must be absolute.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Checkout expiration is required.");
        }
    }
}
