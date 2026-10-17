package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

public record MembershipPromotionSnapshot(
        UUID promotionId,
        String promotionCode,
        String promotionName,
        DiscountType discountType,
        BigDecimal discountValue,
        String promotionCurrency) {

    private static final int MONEY_SCALE = 2;

    public MembershipPromotionSnapshot {

        if (promotionId == null) {
            throw new MembershipValidationException(
                    "Promotion identifier must be provided.");
        }

        promotionCode =
                normalizeRequired(
                        promotionCode,
                        "Promotion code");

        promotionName =
                normalizeRequired(
                        promotionName,
                        "Promotion name");

        if (discountType == null) {
            throw new MembershipValidationException(
                    "Promotion discount type must be provided.");
        }

        discountValue =
                normalizePositiveMoney(
                        discountValue,
                        "Promotion discount value");

        promotionCurrency =
                normalizePromotionCurrency(
                        discountType,
                        promotionCurrency);
    }

    private static String normalizePromotionCurrency(
            DiscountType discountType,
            String currency) {

        if (discountType == DiscountType.PERCENTAGE) {
            if (currency != null
                    && !currency.isBlank()) {

                throw new MembershipValidationException(
                        "Percentage promotion currency must be absent.");
            }

            return null;
        }

        return normalizeCurrency(
                currency,
                "Fixed promotion currency");
    }

    private static BigDecimal normalizePositiveMoney(
            BigDecimal value,
            String field) {

        if (value == null) {
            throw new MembershipValidationException(
                    field + " must be provided.");
        }

        if (value.signum() <= 0) {
            throw new MembershipValidationException(
                    field + " must be positive.");
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
            String value,
            String field) {

        String normalized =
                normalizeRequired(value, field)
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{3}")) {
            throw new MembershipValidationException(
                    field
                            + " must be a three-letter ISO code.");
        }

        return normalized;
    }
}
