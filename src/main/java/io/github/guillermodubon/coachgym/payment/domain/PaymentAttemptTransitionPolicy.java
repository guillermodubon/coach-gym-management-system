package io.github.guillermodubon.coachgym.payment.domain;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import java.time.Instant;
import java.util.UUID;

/** Central lifecycle and terminal-outcome rules for provider payment attempts. */
public final class PaymentAttemptTransitionPolicy {

    private PaymentAttemptTransitionPolicy() {
    }

    public static void requireTransitionAllowed(
            UUID paymentAttemptId,
            PaymentAttemptStatus currentStatus,
            PaymentAttemptStatus requestedStatus) {

        if (paymentAttemptId == null || currentStatus == null || requestedStatus == null) {
            throw new PaymentAttemptValidationException("Payment attempt transition values are required.");
        }
        if (!isAllowed(currentStatus, requestedStatus)) {
            throw new PaymentAttemptStateConflictException(
                    paymentAttemptId, currentStatus, requestedStatus);
        }
    }

    public static void requireSnapshotConsistency(
            PaymentAttemptStatus status,
            PaymentAttemptFailureCode failureCode,
            UUID confirmedPaymentId,
            Instant completedAt) {

        requireOutcomeConsistency(status, failureCode, confirmedPaymentId);
        if (status.isTerminal() != (completedAt != null)) {
            throw new PaymentAttemptValidationException(
                    "Terminal payment attempts must have a completion timestamp and open attempts must not.");
        }
    }

    public static void requireOutcomeConsistency(
            PaymentAttemptStatus status,
            PaymentAttemptFailureCode failureCode,
            UUID confirmedPaymentId) {

        if (status == null) {
            throw new PaymentAttemptValidationException("Payment attempt status is required.");
        }
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            if (confirmedPaymentId == null || failureCode != null) {
                throw new PaymentAttemptValidationException(
                        "A successful payment attempt requires its confirmed payment and no failure code.");
            }
            return;
        }
        if (!status.isTerminal()) {
            if (failureCode != null || confirmedPaymentId != null) {
                throw new PaymentAttemptValidationException(
                        "An open payment attempt cannot have an outcome.");
            }
            return;
        }
        if (failureCode == null || confirmedPaymentId != null) {
            throw new PaymentAttemptValidationException(
                    "An unsuccessful terminal payment attempt requires a failure code and no confirmed payment.");
        }
    }

    private static boolean isAllowed(
            PaymentAttemptStatus currentStatus,
            PaymentAttemptStatus requestedStatus) {

        return switch (currentStatus) {
            case CREATED -> requestedStatus == PaymentAttemptStatus.PROCESSING
                    || requestedStatus == PaymentAttemptStatus.FAILED
                    || requestedStatus == PaymentAttemptStatus.CANCELLED;
            case PROCESSING -> requestedStatus == PaymentAttemptStatus.SUCCEEDED
                    || requestedStatus == PaymentAttemptStatus.FAILED
                    || requestedStatus == PaymentAttemptStatus.CANCELLED
                    || requestedStatus == PaymentAttemptStatus.EXPIRED;
            case SUCCEEDED, FAILED, CANCELLED, EXPIRED -> false;
        };
    }
}
