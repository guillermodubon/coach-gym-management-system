package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable authoritative payment and membership data used to build a receipt.
 *
 * <p>Generation-specific values such as the receipt number, actor, and
 * generation timestamp are deliberately not part of this source projection;
 * they are supplied by the application service at generation time.</p>
 */
public record PaymentReceiptSourceSnapshot(
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
        UUID branchId) {

    /** Compatibility constructor for source fixtures predating branch scoping. */
    public PaymentReceiptSourceSnapshot(
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
            Instant paidAt) {
        this(paymentId, paymentCode, paymentStatus, clientCode, clientDisplayName,
                membershipCode, planName, promotionName, membershipPeriodNumber,
                periodStartsOn, periodEndsOn, listPrice, discountAmount, amount,
                currency, paymentMethod, paidAt, null);
    }

    public PaymentReceiptSourceSnapshot {
        if (paymentId == null || paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment identity is required.");
        }
        if (paymentStatus == null) {
            throw new IllegalArgumentException("Payment status is required.");
        }
        if (clientCode == null || clientCode.isBlank()
                || clientDisplayName == null || clientDisplayName.isBlank()
                || membershipCode == null || membershipCode.isBlank()
                || planName == null || planName.isBlank()) {
            throw new IllegalArgumentException("Receipt source display data is required.");
        }
        if (membershipPeriodNumber < 1 || periodStartsOn == null || periodEndsOn == null
                || periodEndsOn.isBefore(periodStartsOn)) {
            throw new IllegalArgumentException("Receipt source membership period is invalid.");
        }
        if (listPrice == null || discountAmount == null || amount == null
                || listPrice.signum() < 0 || discountAmount.signum() < 0
                || amount.signum() <= 0
                || discountAmount.compareTo(listPrice) > 0
                || listPrice.subtract(discountAmount).compareTo(amount) != 0) {
            throw new IllegalArgumentException("Receipt source financial values are invalid.");
        }
        if (currency == null || currency.isBlank() || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Receipt source currency is invalid.");
        }
        if (paymentMethod == null || paidAt == null) {
            throw new IllegalArgumentException("Receipt source payment values are required.");
        }
        paymentCode = paymentCode.strip();
        clientCode = clientCode.strip();
        clientDisplayName = clientDisplayName.strip();
        membershipCode = membershipCode.strip();
        planName = planName.strip();
        promotionName = promotionName == null || promotionName.isBlank()
                ? null : promotionName.strip();
        currency = currency.strip().toUpperCase(java.util.Locale.ROOT);
        listPrice = listPrice.setScale(2, java.math.RoundingMode.UNNECESSARY);
        discountAmount = discountAmount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
