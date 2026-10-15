package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import java.time.Instant;

record StripeWebhookRequest(byte[] payload, String signatureHeader, Instant verificationTime) {
}
