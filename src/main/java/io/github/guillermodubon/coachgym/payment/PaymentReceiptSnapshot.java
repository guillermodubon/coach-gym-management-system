package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable, server-authoritative data used to render a payment receipt.
 *
 * <p>The snapshot deliberately contains no persistence, HTTP, storage, PDF,
 * or provider-specific types. Once created, it is independent from mutable
 * client, membership, and plan records.</p>
 */
public record PaymentReceiptSnapshot(
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
        UUID branchId) {

    public PaymentReceiptSnapshot(
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
            boolean testMode) {
        this(receiptNumber, paymentId, paymentCode, paymentStatus, clientCode,
                clientDisplayName, membershipCode, planName, promotionName,
                membershipPeriodNumber, periodStartsOn, periodEndsOn, listPrice,
                discountAmount, amount, currency, paymentMethod, paidAt, generatedAt,
                generatedByUserId, generatedByDisplayName, testMode, null);
    }

    private static final int MONEY_SCALE = 2;
    private static final int MAX_TEXT_LENGTH = 200;

    public PaymentReceiptSnapshot {
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
}
