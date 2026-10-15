package io.github.guillermodubon.coachgym.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record PromotionEvaluationRequest(
        UUID promotionId,
        UUID membershipPlanId,
        BigDecimal listPrice,
        String currency,
        LocalDate applicableOn) {

    public PromotionEvaluationRequest {

        if (promotionId == null) {
            throw new IllegalArgumentException(
                    "Promotion identifier must be provided.");
        }

        if (membershipPlanId == null) {
            throw new IllegalArgumentException(
                    "Membership plan identifier must be provided.");
        }

        if (listPrice == null) {
            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure.INVALID_PRICE,
                    "Membership plan price must be provided.");
        }

        if (listPrice.signum() < 0) {
            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure.INVALID_PRICE,
                    "Membership plan price must not be negative.");
        }

        if (currency == null
                || currency.isBlank()) {

            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure.INVALID_CURRENCY,
                    "Membership plan currency must be provided.");
        }

        String normalizedCurrency =
                currency.trim()
                        .toUpperCase(Locale.ROOT);

        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure.INVALID_CURRENCY,
                    "Membership plan currency must be a "
                            + "three-letter ISO code.");
        }

        if (applicableOn == null) {
            throw new IllegalArgumentException(
                    "Promotion application date must be provided.");
        }

        currency = normalizedCurrency;
    }
}