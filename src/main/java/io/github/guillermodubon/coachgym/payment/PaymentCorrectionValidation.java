package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.util.Locale;

final class PaymentCorrectionValidation {

    private PaymentCorrectionValidation() {
    }

    static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }

    static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    static BigDecimal positiveAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be present and positive.");
        }
        return value;
    }

    static String currency(String value) {
        String normalized = requiredText(value, "Refund currency")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Refund currency must be a three-letter code.");
        }
        return normalized;
    }

    static long nonNegativeVersion(long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Payment version must not be negative.");
        }
        return value;
    }
}
