package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import java.net.URI;

record StripeCheckoutCreationRequest(
        String attemptId,
        long amountMinor,
        String currency,
        URI successUrl,
        URI cancelUrl) {
}
