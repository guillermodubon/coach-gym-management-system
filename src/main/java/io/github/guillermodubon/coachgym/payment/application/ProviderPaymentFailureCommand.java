package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import java.time.Instant;
import java.util.UUID;

/** Atomic provider-failure, cancellation, or expiry transition command. */
public record ProviderPaymentFailureCommand(
        UUID paymentAttemptId,
        long expectedVersion,
        PaymentAttemptStatus terminalStatus,
        PaymentAttemptFailureCode failureCode,
        Instant occurredAt) {

    public ProviderPaymentFailureCommand {
        if (paymentAttemptId == null || expectedVersion < 0 || terminalStatus == null
                || !terminalStatus.isTerminal() || terminalStatus == PaymentAttemptStatus.SUCCEEDED
                || failureCode == null || occurredAt == null) {
            throw new IllegalArgumentException("Complete provider failure data is required.");
        }
    }
}
