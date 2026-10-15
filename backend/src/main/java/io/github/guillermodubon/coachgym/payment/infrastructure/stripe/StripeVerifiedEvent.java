package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record StripeVerifiedEvent(
        String eventReference,
        String eventType,
        UUID paymentAttemptId,
        String checkoutReference,
        String providerPaymentReference,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
