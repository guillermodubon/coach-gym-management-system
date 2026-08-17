package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

public record MembershipPricingSnapshot(
        UUID membershipPlanId,
        String planCode,
        String planName,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency,
        MembershipPromotionSnapshot promotion,
        BigDecimal discountAmount,
        BigDecimal finalPrice) {

    private static final int MONEY_SCALE = 2;

    public MembershipPricingSnapshot {

        if (membershipPlanId == null) {
            throw new MembershipValidationException(
                    "Membership plan identifier must be provided.");
        }

        planCode =
                normalizeRequired(
                        planCode,
                        "Membership plan code");

        planName =
                normalizeRequired(
                        planName,
                        "Membership plan name");

        if (durationValue <= 0) {
            throw new MembershipValidationException(
                    "Membership plan duration must be positive.");
        }

        if (durationUnit == null) {
            throw new MembershipValidationException(
                    "Membership plan duration unit must be provided.");
        }

        listPrice =
                normalizeNonNegativeMoney(
                        listPrice,
                        "Membership plan list price");

        currency =
                normalizeCurrency(currency);

        discountAmount =
                normalizeNonNegativeMoney(
                        discountAmount,
                        "Membership discount amount");

        finalPrice =
                normalizeNonNegativeMoney(
                        finalPrice,
                        "Membership final price");

        verifyDiscount(
                promotion,
                listPrice,
                discountAmount,
                finalPrice,
                currency);
    }

    public static MembershipPricingSnapshot withoutPromotion(
            UUID membershipPlanId,
            String planCode,
            String planName,
            int durationValue,
            DurationUnit durationUnit,
            BigDecimal listPrice,
            String currency) {

        BigDecimal normalizedListPrice =
                normalizeNonNegativeMoney(
                        listPrice,
                        "Membership plan list price");

        return new MembershipPricingSnapshot(
                membershipPlanId,
                planCode,
                planName,
                durationValue,
                durationUnit,
                normalizedListPrice,
                currency,
                null,
                BigDecimal.ZERO.setScale(MONEY_SCALE),
                normalizedListPrice);
    }

    private static void verifyDiscount(
            MembershipPromotionSnapshot promotion,
            BigDecimal listPrice,
            BigDecimal discountAmount,
            BigDecimal finalPrice,
            String currency) {

        if (discountAmount.compareTo(listPrice) > 0) {
            throw new MembershipValidationException(
                    "Membership discount amount must not exceed "
                            + "the list price.");
        }

        BigDecimal expectedFinalPrice =
                listPrice.subtract(discountAmount);

        if (finalPrice.compareTo(expectedFinalPrice) != 0) {
            throw new MembershipValidationException(
                    "Membership final price must equal the list price "
                            + "minus the discount amount.");
        }

        if (promotion == null
                && discountAmount.signum() != 0) {

            throw new MembershipValidationException(
                    "Membership without a promotion must not "
                            + "contain a discount.");
        }

        if (promotion != null
                && discountAmount.signum() <= 0) {

            throw new MembershipValidationException(
                    "Membership with a promotion must contain "
                            + "a positive discount.");
        }

        if (promotion != null
                && promotion.promotionCurrency() != null
                && !promotion.promotionCurrency()
                .equals(currency)) {

            throw new MembershipValidationException(
                    "Fixed promotion currency must match "
                            + "the membership plan currency.");
        }
    }

    private static BigDecimal normalizeNonNegativeMoney(
            BigDecimal value,
            String field) {

        if (value == null) {
            throw new MembershipValidationException(
                    field + " must be provided.");
        }

        if (value.signum() < 0) {
            throw new MembershipValidationException(
                    field + " must not be negative.");
        }

        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP);
    }

    private static String normalizeRequired(
            String value,
            String field) {

        if (value == null
                || value.isBlank()) {

            throw new MembershipValidationException(
                    field + " must not be blank.");
        }

        return value.trim();
    }

    private static String normalizeCurrency(
            String value) {

        String normalized =
                normalizeRequired(
                        value,
                        "Membership plan currency")
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{3}")) {
            throw new MembershipValidationException(
                    "Membership plan currency must be a "
                            + "three-letter ISO code.");
        }

        return normalized;
    }
}
