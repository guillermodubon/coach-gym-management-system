package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import java.net.URI;
import java.time.Instant;

record StripeCheckoutResult(
        String checkoutReference,
        URI checkoutUrl,
        Instant expiresAt) {
}
