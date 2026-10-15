package io.github.guillermodubon.coachgym.payment.application;

import java.time.Instant;
import java.util.UUID;

/** Durable command for moving a created attempt to provider processing. */
public record ProcessPaymentAttemptCommand(
        UUID paymentAttemptId,
        long expectedVersion,
        String checkoutReference,
        Instant checkoutExpiresAt,
        UUID actorId,
        Instant occurredAt) {

    public ProcessPaymentAttemptCommand {
        paymentAttemptId = PaymentAttemptCommandValidation.requiredIdentifier(
                paymentAttemptId, "Payment attempt id");
        PaymentAttemptCommandValidation.nonNegativeVersion(expectedVersion);
        if (checkoutReference == null || checkoutReference.isBlank()) {
            throw new IllegalArgumentException("Checkout reference is required.");
        }
        checkoutReference = checkoutReference.strip();
        if (checkoutExpiresAt == null) {
            throw new IllegalArgumentException("Checkout expiration is required.");
        }
        actorId = PaymentAttemptCommandValidation.requiredIdentifier(actorId, "Actor id");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamp is required.");
        }
    }
}
