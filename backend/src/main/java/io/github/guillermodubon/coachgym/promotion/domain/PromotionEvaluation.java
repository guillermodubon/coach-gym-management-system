package io.github.guillermodubon.coachgym.promotion.domain;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable result of evaluating a promotion against a commercial price.
 */
public record PromotionEvaluation(
        DiscountType discountType,
        BigDecimal discountValue,
        String promotionCurrency,
        BigDecimal listPrice,
        String currency,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        LocalDate evaluationDate) {

    public PromotionEvaluation {
        if (discountType == null) {
            throw new IllegalArgumentException(
                    "Discount type must be provided.");
        }

        if (discountValue == null) {
            throw new IllegalArgumentException(
                    "Discount value must be provided.");
        }

        if (listPrice == null) {
            throw new IllegalArgumentException(
                    "List price must be provided.");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency must be provided.");
        }

        if (discountAmount == null) {
            throw new IllegalArgumentException(
                    "Discount amount must be provided.");
        }

        if (finalPrice == null) {
            throw new IllegalArgumentException(
                    "Final price must be provided.");
        }

        if (evaluationDate == null) {
            throw new IllegalArgumentException(
                    "Evaluation date must be provided.");
        }
    }
}
