package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Shared normalization and validation for administrative payment corrections. */
final class PaymentCorrectionCommandValidation {

    static final int MIN_REASON_LENGTH = 3;
    static final int MAX_REASON_LENGTH = 2000;
    static final int MAX_EXTERNAL_REFERENCE_LENGTH = 128;

    private PaymentCorrectionCommandValidation() {
    }

    static UUID paymentId(UUID value) {
        if (value == null) {
            throw new PaymentCorrectionValidationException(
                    "Payment id is required.");
        }
        return value;
    }

    static String reason(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentCorrectionValidationException(
                    "Payment correction reason is required.");
        }
        String normalized = value.strip();
        if (normalized.length() < MIN_REASON_LENGTH) {
            throw new PaymentCorrectionValidationException(
                    "Payment correction reason must contain at least "
                            + MIN_REASON_LENGTH + " characters.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new PaymentCorrectionValidationException(
                    "Payment correction reason must not exceed "
                            + MAX_REASON_LENGTH + " characters.");
        }
        return normalized;
    }

    static String externalReference(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_EXTERNAL_REFERENCE_LENGTH) {
            throw new PaymentCorrectionValidationException(
                    "Refund external reference must not exceed "
                            + MAX_EXTERNAL_REFERENCE_LENGTH + " characters.");
        }
        return normalized;
    }

    static long expectedVersion(long value) {
        if (value < 0) {
            throw new PaymentCorrectionValidationException(
                    "Payment version must not be negative.");
        }
        return value;
    }
}
