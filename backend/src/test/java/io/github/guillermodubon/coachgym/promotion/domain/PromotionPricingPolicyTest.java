package io.github.guillermodubon.coachgym.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PromotionPricingPolicyTest {

    private static final LocalDate VALID_FROM =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate VALID_UNTIL =
            LocalDate.of(2026, 9, 30);

    private static final LocalDate EVALUATION_DATE =
            LocalDate.of(2026, 9, 15);

    @Test
    void calculatesPercentageDiscount() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("20.00"),
                        new BigDecimal("25.00"),
                        "USD",
                        EVALUATION_DATE);

        assertThat(evaluation.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(evaluation.discountAmount())
                .isEqualByComparingTo("5.00");

        assertThat(evaluation.finalPrice())
                .isEqualByComparingTo("20.00");

        assertThat(evaluation.currency()).isEqualTo("USD");
        assertThat(evaluation.promotionCurrency()).isNull();
    }

    @Test
    void roundsPercentageDiscountHalfUp() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("12.50"),
                        new BigDecimal("19.99"),
                        "USD",
                        EVALUATION_DATE);

        assertThat(evaluation.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(evaluation.finalPrice())
                .isEqualByComparingTo("17.49");
    }

    @Test
    void calculatesFixedAmountDiscount() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        fixedAmountPromotion("5.00", "USD"),
                        new BigDecimal("25.00"),
                        " usd ",
                        EVALUATION_DATE);

        assertThat(evaluation.discountAmount())
                .isEqualByComparingTo("5.00");

        assertThat(evaluation.finalPrice())
                .isEqualByComparingTo("20.00");

        assertThat(evaluation.currency()).isEqualTo("USD");
        assertThat(evaluation.promotionCurrency())
                .isEqualTo("USD");
    }

    @Test
    void limitsDiscountToListPrice() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        fixedAmountPromotion("15.00", "USD"),
                        new BigDecimal("10.00"),
                        "USD",
                        EVALUATION_DATE);

        assertThat(evaluation.discountAmount())
                .isEqualByComparingTo("10.00");

        assertThat(evaluation.finalPrice())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void acceptsPromotionStartDate() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "USD",
                        VALID_FROM);

        assertThat(evaluation.evaluationDate())
                .isEqualTo(VALID_FROM);
    }

    @Test
    void acceptsPromotionEndDate() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "USD",
                        VALID_UNTIL);

        assertThat(evaluation.evaluationDate())
                .isEqualTo(VALID_UNTIL);
    }

    @Test
    void rejectsDateBeforeValidityPeriod() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "USD",
                        VALID_FROM.minusDays(1)))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion is outside its validity period.");
    }

    @Test
    void rejectsDateAfterValidityPeriod() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "USD",
                        VALID_UNTIL.plusDays(1)))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion is outside its validity period.");
    }

    @Test
    void rejectsFixedAmountCurrencyMismatch() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        fixedAmountPromotion("5.00", "USD"),
                        new BigDecimal("25.00"),
                        "EUR",
                        EVALUATION_DATE))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Fixed amount promotion currency does not match "
                                + "the evaluated price currency.");
    }

    @Test
    void percentagePromotionUsesEvaluationCurrency() {
        PromotionEvaluation evaluation =
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("50.00"),
                        " eur ",
                        EVALUATION_DATE);

        assertThat(evaluation.currency()).isEqualTo("EUR");
        assertThat(evaluation.discountAmount())
                .isEqualByComparingTo("5.00");
    }

    @Test
    void rejectsNegativeListPrice() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("-1.00"),
                        "USD",
                        EVALUATION_DATE))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage("List price must not be negative.");
    }

    @Test
    void rejectsListPriceWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("19.999"),
                        "USD",
                        EVALUATION_DATE))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "List price must have at most two decimal places.");
    }

    @Test
    void rejectsInvalidEvaluationCurrency() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "US",
                        EVALUATION_DATE))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Evaluation currency must be a three-letter ISO code.");
    }

    @Test
    void rejectsMissingEvaluationDate() {
        assertThatThrownBy(() ->
                PromotionPricingPolicy.evaluate(
                        percentagePromotion("10.00"),
                        new BigDecimal("20.00"),
                        "USD",
                        null))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage("Evaluation date must be provided.");
    }

    private static PromotionDefinition percentagePromotion(
            String percentage) {

        return PromotionDefinition.create(
                "Percentage Promotion",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal(percentage),
                null,
                VALID_FROM,
                VALID_UNTIL);
    }

    private static PromotionDefinition fixedAmountPromotion(
            String amount,
            String currency) {

        return PromotionDefinition.create(
                "Fixed Amount Promotion",
                null,
                DiscountType.FIXED_AMOUNT,
                new BigDecimal(amount),
                currency,
                VALID_FROM,
                VALID_UNTIL);
    }
}