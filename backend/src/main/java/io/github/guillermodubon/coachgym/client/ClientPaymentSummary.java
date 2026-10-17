package io.github.guillermodubon.coachgym.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Basic payment snapshot for the current membership period. */
public record ClientPaymentSummary(
        UUID paymentId,
        String paymentCode,
        String status,
        BigDecimal amount,
        String currency,
        Instant paidAt) {

    public ClientPaymentSummary {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        paymentCode = required(paymentCode, "Payment code");
        status = required(status, "Payment status");
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be present and non-negative.");
        }
        currency = required(currency, "Payment currency")
                .toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Payment currency must be a three-letter code.");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("Payment timestamp is required.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
