package io.github.guillermodubon.coachgym.payment.application;

import java.time.Instant;
import java.util.UUID;

/** Durable command for recording provider-confirmed staff cancellation. */
public record CancelPaymentAttemptPersistenceCommand(
        UUID paymentAttemptId,
        long expectedVersion,
        UUID actorId,
        Instant occurredAt) {

    public CancelPaymentAttemptPersistenceCommand {
        paymentAttemptId = PaymentAttemptCommandValidation.requiredIdentifier(
                paymentAttemptId, "Payment attempt id");
        PaymentAttemptCommandValidation.nonNegativeVersion(expectedVersion);
        actorId = PaymentAttemptCommandValidation.requiredIdentifier(actorId, "Actor id");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamp is required.");
        }
    }
}
