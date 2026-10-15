package io.github.guillermodubon.coachgym.plan.domain;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Immutable, validated commercial definition used to create or update a plan. */
public record PlanDefinition(
        String name,
        String description,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency) {

    private static final int MAX_DURATION_VALUE = Short.MAX_VALUE;
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;

    public static PlanDefinition create(
            String name,
            String description,
            int durationValue,
            DurationUnit durationUnit,
            BigDecimal listPrice,
            String currency) {
        String normalizedName = normalizeRequired(name, "Plan name");
        if (normalizedName.length() > 160) {
            throw new PlanValidationException("Plan name must not exceed 160 characters.");
        }
        String normalizedDescription = normalizeOptional(description);
        if (normalizedDescription != null && normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new PlanValidationException("Plan description must not exceed 2000 characters.");
        }
        if (durationValue <= 0 || durationValue > MAX_DURATION_VALUE) {
            throw new PlanValidationException("Plan duration must be between 1 and 32767.");
        }
        if (durationUnit == null) {
            throw new PlanValidationException("Plan duration unit must be provided.");
        }
        BigDecimal normalizedPrice = normalizePrice(listPrice);
        String normalizedCurrency = normalizeCurrency(currency);
        return new PlanDefinition(
                normalizedName,
                normalizedDescription,
                durationValue,
                durationUnit,
                normalizedPrice,
                normalizedCurrency);
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PlanValidationException(field + " must not be blank.");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        if (value == null) {
            throw new PlanValidationException("List price must be provided.");
        }
        if (value.signum() < 0) {
            throw new PlanValidationException("List price cannot be negative.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PlanValidationException("List price must have at most two decimal places.");
        }
    }

    private static String normalizeCurrency(String value) {
        String normalized = normalizeRequired(value, "Currency").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new PlanValidationException("Currency must be a three-letter ISO code.");
        }
        return normalized;
    }
}
