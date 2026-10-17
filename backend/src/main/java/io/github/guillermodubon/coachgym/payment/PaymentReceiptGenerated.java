package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Privacy-safe public event for a successfully generated canonical receipt. */
public record PaymentReceiptGenerated(
        UUID receiptId,
        String receiptNumber,
        UUID paymentId,
        String paymentCode,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String currency,
        UUID generatedByUserId,
        String actorIdentifier,
        boolean testMode,
        Instant occurredAt,
        UUID branchId) {

    public PaymentReceiptGenerated(UUID receiptId, String receiptNumber,
            UUID paymentId, String paymentCode, PaymentStatus paymentStatus,
            BigDecimal amount, String currency, UUID generatedByUserId,
            String actorIdentifier, boolean testMode, Instant occurredAt) {
        this(receiptId, receiptNumber, paymentId, paymentCode, paymentStatus,
                amount, currency, generatedByUserId, actorIdentifier, testMode,
                occurredAt, null);
    }

    public PaymentReceiptGenerated {
        if (receiptId == null || paymentId == null || generatedByUserId == null) {
            throw new IllegalArgumentException("Receipt event identifiers are required.");
        }
        receiptNumber = requiredText(receiptNumber, "Receipt number");
        paymentCode = requiredText(paymentCode, "Payment code");
        if (paymentStatus == null) {
            throw new IllegalArgumentException("Receipt event payment status is required.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Receipt event amount must be positive.");
        }
        if (currency == null || !currency.strip().toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Receipt event currency must be a three-letter code.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        actorIdentifier = requiredText(actorIdentifier, "Receipt actor identifier");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Receipt event timestamp is required.");
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.strip();
    }
}
