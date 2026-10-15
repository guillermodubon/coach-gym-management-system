package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Staff request to cancel an open provider checkout using optimistic locking. */
public record CancelPaymentAttemptCommand(
        UUID paymentAttemptId,
        long expectedVersion) {

    public CancelPaymentAttemptCommand {
        paymentAttemptId = PaymentAttemptCommandValidation.requiredIdentifier(
                paymentAttemptId, "Payment attempt id");
        PaymentAttemptCommandValidation.nonNegativeVersion(expectedVersion);
    }
}
