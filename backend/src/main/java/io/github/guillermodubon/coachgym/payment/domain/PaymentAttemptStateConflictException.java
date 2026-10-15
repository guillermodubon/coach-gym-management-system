package io.github.guillermodubon.coachgym.payment.domain;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import java.util.UUID;

/** Raised when a payment attempt is asked to perform an invalid state transition. */
public class PaymentAttemptStateConflictException extends RuntimeException {

    public PaymentAttemptStateConflictException(
            UUID paymentAttemptId,
            PaymentAttemptStatus currentStatus,
            PaymentAttemptStatus requestedStatus) {
        super("Payment attempt %s cannot transition from %s to %s."
                .formatted(paymentAttemptId, currentStatus, requestedStatus));
    }
}
