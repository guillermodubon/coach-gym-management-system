package io.github.guillermodubon.coachgym.payment;

import java.time.Instant;
import java.util.UUID;

/** Result of an administrative void or full refund operation. */
public record PaymentCorrectionDetails(
        UUID paymentId,
        String paymentCode,
        PaymentCorrectionType correctionType,
        PaymentStatus previousStatus,
        PaymentStatus currentStatus,
        String reason,
        Instant correctedAt,
        UUID correctedByUserId,
        long version,
        PaymentRefundDetails refund) {

    public PaymentCorrectionDetails {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        paymentCode = PaymentCorrectionValidation.requiredText(
                paymentCode, "Payment code");
        if (correctionType == null) {
            throw new IllegalArgumentException(
                    "Payment correction type is required.");
        }
        if (previousStatus != PaymentStatus.PAID) {
            throw new IllegalArgumentException(
                    "Payment correction must start from PAID.");
        }
        validateTargetStatus(correctionType, currentStatus, refund);
        reason = PaymentCorrectionValidation.requiredText(
                reason, "Payment correction reason");
        if (correctedAt == null) {
            throw new IllegalArgumentException(
                    "Payment correction timestamp is required.");
        }
        if (correctedByUserId == null) {
            throw new IllegalArgumentException(
                    "Payment correction actor is required.");
        }
        PaymentCorrectionValidation.nonNegativeVersion(version);
        if (refund != null && !paymentId.equals(refund.paymentId())) {
            throw new IllegalArgumentException(
                    "Refund must belong to the corrected payment.");
        }
    }

    public static PaymentCorrectionDetails voided(
            UUID paymentId,
            String paymentCode,
            String reason,
            Instant correctedAt,
            UUID correctedByUserId,
            long version) {
        return new PaymentCorrectionDetails(
                paymentId,
                paymentCode,
                PaymentCorrectionType.VOID,
                PaymentStatus.PAID,
                PaymentStatus.VOIDED,
                reason,
                correctedAt,
                correctedByUserId,
                version,
                null);
    }

    public static PaymentCorrectionDetails refunded(
            UUID paymentId,
            String paymentCode,
            String reason,
            Instant correctedAt,
            UUID correctedByUserId,
            long version,
            PaymentRefundDetails refund) {
        return new PaymentCorrectionDetails(
                paymentId,
                paymentCode,
                PaymentCorrectionType.REFUND,
                PaymentStatus.PAID,
                PaymentStatus.REFUNDED,
                reason,
                correctedAt,
                correctedByUserId,
                version,
                refund);
    }

    private static void validateTargetStatus(
            PaymentCorrectionType type,
            PaymentStatus currentStatus,
            PaymentRefundDetails refund) {
        if (type == PaymentCorrectionType.VOID) {
            if (currentStatus != PaymentStatus.VOIDED) {
                throw new IllegalArgumentException(
                        "VOID correction must finish in VOIDED.");
            }
            if (refund != null) {
                throw new IllegalArgumentException(
                        "VOID correction must not contain refund details.");
            }
            return;
        }

        if (currentStatus != PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException(
                    "REFUND correction must finish in REFUNDED.");
        }
        if (refund == null) {
            throw new IllegalArgumentException(
                    "REFUND correction requires refund details.");
        }
    }
}
