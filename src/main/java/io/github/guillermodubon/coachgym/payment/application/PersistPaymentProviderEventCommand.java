package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

/** Safe provider-event identity used only for durable idempotency. */
public record PersistPaymentProviderEventCommand(
        UUID id,
        PaymentProvider provider,
        String providerEventReference,
        PaymentProviderEventType eventType,
        UUID paymentAttemptId,
        Instant receivedAt) {
}
