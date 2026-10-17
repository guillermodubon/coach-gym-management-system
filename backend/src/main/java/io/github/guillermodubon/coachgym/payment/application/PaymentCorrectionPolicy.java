package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;

/** Domain policy for final administrative payment state transitions. */
public final class PaymentCorrectionPolicy {

    private PaymentCorrectionPolicy() {
    }

    public static void requireVoidAllowed(
            UUID paymentId,
            PaymentStatus currentStatus) {
        requireTransitionAllowed(
                paymentId,
                currentStatus,
                PaymentStatus.VOIDED);
    }

    public static void requireRefundAllowed(
            UUID paymentId,
            PaymentStatus currentStatus) {
        requireTransitionAllowed(
                paymentId,
                currentStatus,
                PaymentStatus.REFUNDED);
    }

    public static void requireTransitionAllowed(
            UUID paymentId,
            PaymentStatus currentStatus,
            PaymentStatus requestedStatus) {
        PaymentCorrectionCommandValidation.paymentId(paymentId);
        if (currentStatus == null) {
            throw new PaymentCorrectionValidationException(
                    "Current payment status is required.");
        }
        if (requestedStatus == null) {
            throw new PaymentCorrectionValidationException(
                    "Requested payment status is required.");
        }

        boolean allowed = currentStatus == PaymentStatus.PAID
                && (requestedStatus == PaymentStatus.VOIDED
                    || requestedStatus == PaymentStatus.REFUNDED);

        if (!allowed) {
            throw new PaymentCorrectionStateConflictException(
                    paymentId,
                    currentStatus,
                    requestedStatus);
        }
    }

    public static void requireExpectedVersion(
            UUID paymentId,
            long expectedVersion,
            long currentVersion) {
        PaymentCorrectionCommandValidation.paymentId(paymentId);
        PaymentCorrectionCommandValidation.expectedVersion(expectedVersion);
        PaymentCorrectionCommandValidation.expectedVersion(currentVersion);
        if (expectedVersion != currentVersion) {
            throw new PaymentCorrectionVersionConflictException(
                    paymentId,
                    expectedVersion,
                    currentVersion);
        }
    }
}
