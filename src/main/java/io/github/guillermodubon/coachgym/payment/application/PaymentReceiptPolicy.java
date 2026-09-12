package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;

/** Business policy for receipt eligibility, independent of persistence or HTTP. */
public final class PaymentReceiptPolicy {

    private PaymentReceiptPolicy() {
    }

    public static boolean isGenerationEligible(PaymentStatus status) {
        return status == PaymentStatus.PAID;
    }

    public static void requireGenerationAllowed(
            UUID paymentId,
            PaymentStatus currentStatus) {

        if (paymentId == null) {
            throw new PaymentReceiptValidationException("Payment id is required.");
        }
        if (currentStatus == null) {
            throw new PaymentReceiptValidationException("Current payment status is required.");
        }
        if (!isGenerationEligible(currentStatus)) {
            throw new PaymentReceiptStateConflictException(paymentId, currentStatus);
        }
    }
}
