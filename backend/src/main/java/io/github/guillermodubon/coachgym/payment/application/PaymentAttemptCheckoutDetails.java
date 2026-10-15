package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import java.net.URI;
import java.time.Instant;

/** Staff-facing checkout result. Provider references remain outside this response contract. */
public record PaymentAttemptCheckoutDetails(
        PaymentAttemptDetails attempt,
        URI checkoutUrl,
        Instant expiresAt) {

    public PaymentAttemptCheckoutDetails {
        if (attempt == null) {
            throw new IllegalArgumentException("Payment attempt details are required.");
        }
        if (checkoutUrl == null || !checkoutUrl.isAbsolute()) {
            throw new IllegalArgumentException("Checkout URL must be absolute.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Checkout expiration is required.");
        }
    }
}
