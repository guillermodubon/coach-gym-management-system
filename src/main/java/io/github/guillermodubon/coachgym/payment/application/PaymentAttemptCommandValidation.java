package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

final class PaymentAttemptCommandValidation {

    private PaymentAttemptCommandValidation() {
    }

    static UUID requiredIdentifier(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    static long nonNegativeVersion(long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Payment attempt version must not be negative.");
        }
        return value;
    }
}
