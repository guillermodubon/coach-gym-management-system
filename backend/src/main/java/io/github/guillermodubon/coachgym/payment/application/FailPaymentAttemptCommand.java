package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import java.time.Instant;
import java.util.UUID;

/** Durable command for recording a provider failure without creating a payment. */
public record FailPaymentAttemptCommand(
        UUID paymentAttemptId,
        long expectedVersion,
        PaymentAttemptFailureCode failureCode,
        UUID actorId,
        Instant occurredAt) {

    public FailPaymentAttemptCommand {
        paymentAttemptId = PaymentAttemptCommandValidation.requiredIdentifier(
                paymentAttemptId, "Payment attempt id");
        PaymentAttemptCommandValidation.nonNegativeVersion(expectedVersion);
        if (failureCode == null || failureCode == PaymentAttemptFailureCode.PROVIDER_CANCELLED
                || failureCode == PaymentAttemptFailureCode.PROVIDER_EXPIRED) {
            throw new IllegalArgumentException("A provider failure code is required.");
        }
        actorId = PaymentAttemptCommandValidation.requiredIdentifier(actorId, "Actor id");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Payment attempt timestamp is required.");
        }
    }
}
