package io.github.guillermodubon.coachgym.promotion.domain;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Immutable and validated commercial definition used to create or update a promotion.
 */
public record PromotionDefinition(
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        String currency,
        LocalDate validFrom,
        LocalDate validUntil) {

    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;
    private static final int DISCOUNT_SCALE = 2;

    private static final BigDecimal MAX_PERCENTAGE =
            new BigDecimal("100.00");

    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile("[A-Z]{3}");

    public static PromotionDefinition create(
            String name,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            String currency,
            LocalDate validFrom,
            LocalDate validUntil) {

        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeDescription(description);
        DiscountType validatedType = requireDiscountType(discountType);
        BigDecimal normalizedValue = normalizeDiscountValue(discountValue);
        String normalizedCurrency = normalizeCurrency(currency);
        LocalDate normalizedValidFrom =
                requireDate(validFrom, "Promotion start date");
        LocalDate normalizedValidUntil =
                requireDate(validUntil, "Promotion end date");

        validateDateRange(normalizedValidFrom, normalizedValidUntil);
        validateDiscountDefinition(
                validatedType,
                normalizedValue,
                normalizedCurrency);

        return new PromotionDefinition(
                normalizedName,
                normalizedDescription,
                validatedType,
                normalizedValue,
                normalizedCurrency,
                normalizedValidFrom,
                normalizedValidUntil);
    }

    private static String normalizeName(String value) {
        String normalized = normalizeRequired(value, "Promotion name");

        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new PromotionValidationException(
                    "Promotion name must not exceed 160 characters.");
        }

        return normalized;
    }

    private static String normalizeDescription(String value) {
        String normalized = normalizeOptional(value);

        if (normalized != null
                && normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new PromotionValidationException(
                    "Promotion description must not exceed 2000 characters.");
        }

        return normalized;
    }

    private static DiscountType requireDiscountType(
            DiscountType discountType) {

        if (discountType == null) {
            throw new PromotionValidationException(
                    "Promotion discount type must be provided.");
        }

        return discountType;
    }

    private static BigDecimal normalizeDiscountValue(BigDecimal value) {
        if (value == null) {
            throw new PromotionValidationException(
                    "Promotion discount value must be provided.");
        }

        if (value.signum() <= 0) {
            throw new PromotionValidationException(
                    "Promotion discount value must be greater than zero.");
        }

        try {
            return value.setScale(DISCOUNT_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PromotionValidationException(
                    "Promotion discount value must have at most two decimal places.");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw new PromotionValidationException(
                    "Promotion currency must be a three-letter ISO code.");
        }

        return normalized;
    }

    private static LocalDate requireDate(
            LocalDate value,
            String fieldName) {

        if (value == null) {
            throw new PromotionValidationException(
                    fieldName + " must be provided.");
        }

        return value;
    }

    private static void validateDateRange(
            LocalDate validFrom,
            LocalDate validUntil) {

        if (validFrom.isAfter(validUntil)) {
            throw new PromotionValidationException(
                    "Promotion start date must not be after its end date.");
        }
    }

    private static void validateDiscountDefinition(
            DiscountType type,
            BigDecimal value,
            String currency) {

        switch (type) {
            case PERCENTAGE -> validatePercentage(value, currency);
            case FIXED_AMOUNT -> validateFixedAmount(currency);
        }
    }

    private static void validatePercentage(
            BigDecimal value,
            String currency) {

        if (value.compareTo(MAX_PERCENTAGE) > 0) {
            throw new PromotionValidationException(
                    "Percentage discount must not exceed 100.");
        }

        if (currency != null) {
            throw new PromotionValidationException(
                    "Percentage discount must not define a currency.");
        }
    }

    private static void validateFixedAmount(String currency) {
        if (currency == null) {
            throw new PromotionValidationException(
                    "Fixed amount discount must define a currency.");
        }
    }

    private static String normalizeRequired(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new PromotionValidationException(
                    fieldName + " must not be blank.");
        }

        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}