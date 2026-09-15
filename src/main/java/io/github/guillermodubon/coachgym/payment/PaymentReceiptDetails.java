package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Immutable receipt metadata and financial snapshot returned by the payment module. */
public record PaymentReceiptDetails(
        UUID id,
        String receiptNumber,
        UUID paymentId,
        String paymentCode,
        PaymentStatus paymentStatus,
        String clientCode,
        String clientDisplayName,
        String membershipCode,
        String planName,
        String promotionName,
        int membershipPeriodNumber,
        LocalDate periodStartsOn,
        LocalDate periodEndsOn,
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        Instant paidAt,
        Instant generatedAt,
        UUID generatedByUserId,
        String generatedByDisplayName,
        boolean testMode,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String rendererVersion,
        long version) {

    private static final int MONEY_SCALE = 2;
    private static final int MAX_TEXT_LENGTH = 200;
    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024L * 1024L;

    public PaymentReceiptDetails {
        id = requiredIdentifier(id, "Receipt id");
        receiptNumber = requiredText(receiptNumber, "Receipt number");
        paymentId = requiredIdentifier(paymentId, "Payment id");
        paymentCode = requiredText(paymentCode, "Payment code");
        if (paymentStatus == null) {
            throw new IllegalArgumentException("Payment status is required.");
        }
        clientCode = requiredText(clientCode, "Client code");
        clientDisplayName = requiredText(clientDisplayName, "Client display name");
        membershipCode = requiredText(membershipCode, "Membership code");
        planName = requiredText(planName, "Plan name");
        promotionName = optionalText(promotionName);
        if (membershipPeriodNumber < 1) {
            throw new IllegalArgumentException("Membership period number must be positive.");
        }
        if (periodStartsOn == null || periodEndsOn == null) {
            throw new IllegalArgumentException("Membership period dates are required.");
        }
        if (periodEndsOn.isBefore(periodStartsOn)) {
            throw new IllegalArgumentException("Membership period end must not precede its start.");
        }
        listPrice = nonNegativeMoney(listPrice, "List price");
        discountAmount = nonNegativeMoney(discountAmount, "Discount amount");
        amount = positiveMoney(amount, "Payment amount");
        if (discountAmount.compareTo(listPrice) > 0) {
            throw new IllegalArgumentException("Discount amount must not exceed the list price.");
        }
        if (listPrice.subtract(discountAmount).compareTo(amount) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount must equal the list price minus the discount amount.");
        }
        currency = normalizeCurrency(currency);
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required.");
        }
        if (paidAt == null || generatedAt == null) {
            throw new IllegalArgumentException("Receipt timestamps are required.");
        }
        if (generatedAt.isBefore(paidAt)) {
            throw new IllegalArgumentException("Receipt generation cannot precede payment time.");
        }
        generatedByUserId = requiredIdentifier(generatedByUserId, "Receipt actor id");
        generatedByDisplayName = optionalText(generatedByDisplayName);
        contentType = normalizeContentType(contentType);
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Receipt content type must be application/pdf.");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_DOCUMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("Receipt document size is invalid.");
        }
        checksumSha256 = normalizeChecksum(checksumSha256);
        rendererVersion = optionalText(rendererVersion);
        if (version < 0) {
            throw new IllegalArgumentException("Receipt version must not be negative.");
        }
    }

    /** Reconstructs the immutable renderer input from the persisted metadata snapshot. */
    public PaymentReceiptSnapshot snapshot() {
        return new PaymentReceiptSnapshot(
                receiptNumber,
                paymentId,
                paymentCode,
                paymentStatus,
                clientCode,
                clientDisplayName,
                membershipCode,
                planName,
                promotionName,
                membershipPeriodNumber,
                periodStartsOn,
                periodEndsOn,
                listPrice,
                discountAmount,
                amount,
                currency,
                paymentMethod,
                paidAt,
                generatedAt,
                generatedByUserId,
                generatedByDisplayName,
                testMode);
    }

    private static UUID requiredIdentifier(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " must not exceed " + MAX_TEXT_LENGTH + " characters.");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Receipt text must not exceed " + MAX_TEXT_LENGTH + " characters.");
        }
        return normalized;
    }

    private static BigDecimal positiveMoney(BigDecimal value, String field) {
        BigDecimal normalized = money(value, field);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero.");
        }
        return normalized;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        BigDecimal normalized = money(value, field);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new IllegalArgumentException(field + " must not exceed two decimal places.");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Receipt currency is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Receipt currency must be a three-letter ISO code.");
        }
        return normalized;
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Receipt content type is required.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeChecksum(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Receipt checksum must be a SHA-256 hexadecimal value.");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
