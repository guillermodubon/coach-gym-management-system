package io.github.guillermodubon.coachgym.promotion.domain;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Calculates the monetary result of applying a promotion.
 */
public final class PromotionPricingPolicy {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Z]{3}");

    private PromotionPricingPolicy() {
        // Private constructor to prevent instantiation
    }

    public static PromotionEvaluation evaluate(
            PromotionDefinition promotion,
            BigDecimal listPrice,
            String currency,
            LocalDate evaluationDate) {

        if (promotion == null) {
            throw new PromotionValidationException("Promotion definition must be provided.");
        }

        BigDecimal normalizedListPrice = normalizeListPrice(listPrice);
        String normalizedCurrency = normalizeCurrency(currency);
        LocalDate validatedDate = requireEvaluationDate(evaluationDate);

        validatePromotionDate(promotion, validatedDate);
        validateCurrencyCompatibility(promotion, normalizedCurrency);

        BigDecimal calculatedDiscount = calculateDiscount(promotion, normalizedListPrice);
        BigDecimal appliedDiscount = calculatedDiscount.min(normalizedListPrice);
        BigDecimal finalPrice = normalizedListPrice.subtract(appliedDiscount);

        return new PromotionEvaluation(
                promotion.discountType(),
                promotion.discountValue(),
                promotion.currency(),
                normalizedListPrice,
                normalizedCurrency,
                appliedDiscount,
                finalPrice,
                validatedDate
        );
    }

    private static BigDecimal calculateDiscount(PromotionDefinition promotion, BigDecimal listPrice) {
        return switch (promotion.discountType()) {
            case PERCENTAGE -> listPrice
                    .multiply(promotion.discountValue())
                    .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promotion.discountValue()
                    .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        };
    }

    private static BigDecimal normalizeListPrice(BigDecimal value) {
        if (value == null) {
            throw new PromotionValidationException("List price must be provided.");
        }
        if (value.signum() < 0) {
            throw new PromotionValidationException("List price must not be negative.");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PromotionValidationException("List price must have at most two decimal places.");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new PromotionValidationException("Evaluation currency must be provided.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw new PromotionValidationException("Evaluation currency must be a three-letter ISO code.");
        }
        return normalized;
    }

    private static LocalDate requireEvaluationDate(LocalDate value) {
        if (value == null) {
            throw new PromotionValidationException("Evaluation date must be provided.");
        }
        return value;
    }

    private static void validatePromotionDate(PromotionDefinition promotion, LocalDate evaluationDate) {
        boolean beforeStart = evaluationDate.isBefore(promotion.validFrom());
        boolean afterEnd = evaluationDate.isAfter(promotion.validUntil());
        if (beforeStart || afterEnd) {
            throw new PromotionValidationException("Promotion is outside its validity period.");
        }
    }

    private static void validateCurrencyCompatibility(PromotionDefinition promotion, String evaluationCurrency) {
        if (promotion.discountType() != DiscountType.FIXED_AMOUNT) {
            return;
        }
        if (!promotion.currency().equals(evaluationCurrency)) {
            throw new PromotionValidationException(
                    "Fixed amount promotion currency does not match the evaluated price currency."
            );
        }
    }
}
