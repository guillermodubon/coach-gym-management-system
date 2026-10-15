package io.github.guillermodubon.coachgym.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PromotionDefinitionTest {

    private static final LocalDate VALID_FROM =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate VALID_UNTIL =
            LocalDate.of(2026, 9, 30);

    @Test
    void createsAndNormalizesPercentagePromotion() {
        PromotionDefinition definition = PromotionDefinition.create(
                " September Discount ",
                " Twenty percent off selected plans. ",
                DiscountType.PERCENTAGE,
                new BigDecimal("20"),
                null,
                VALID_FROM,
                VALID_UNTIL);

        assertThat(definition.name()).isEqualTo("September Discount");
        assertThat(definition.description())
                .isEqualTo("Twenty percent off selected plans.");
        assertThat(definition.discountValue())
                .isEqualByComparingTo("20.00");
        assertThat(definition.currency()).isNull();
    }

    @Test
    void createsAndNormalizesFixedAmountPromotion() {
        PromotionDefinition definition = PromotionDefinition.create(
                "Monthly Discount",
                " ",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("5"),
                " usd ",
                VALID_FROM,
                VALID_UNTIL);

        assertThat(definition.description()).isNull();
        assertThat(definition.discountValue())
                .isEqualByComparingTo("5.00");
        assertThat(definition.currency()).isEqualTo("USD");
    }

    @Test
    void acceptsSingleDayPromotion() {
        LocalDate date = LocalDate.of(2026, 9, 15);

        PromotionDefinition definition = PromotionDefinition.create(
                "Single Day Promotion",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null,
                date,
                date);

        assertThat(definition.validFrom()).isEqualTo(date);
        assertThat(definition.validUntil()).isEqualTo(date);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> percentage(" ", "10.00"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage("Promotion name must not be blank.");
    }

    @Test
    void rejectsZeroOrNegativeValue() {
        assertThatThrownBy(() -> percentage("Promotion", "0.00"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion discount value must be greater than zero.");

        assertThatThrownBy(() -> fixedAmount("-1.00", "USD"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion discount value must be greater than zero.");
    }

    @Test
    void rejectsPercentageGreaterThanOneHundred() {
        assertThatThrownBy(() -> percentage("Promotion", "100.01"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage("Percentage discount must not exceed 100.");
    }

    @Test
    void rejectsPercentageWithCurrency() {
        assertThatThrownBy(() -> PromotionDefinition.create(
                "Promotion",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("20.00"),
                "USD",
                VALID_FROM,
                VALID_UNTIL))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Percentage discount must not define a currency.");
    }

    @Test
    void rejectsFixedAmountWithoutCurrency() {
        assertThatThrownBy(() -> fixedAmount("5.00", null))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Fixed amount discount must define a currency.");
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> fixedAmount("5.00", "US"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion currency must be a three-letter ISO code.");
    }

    @Test
    void rejectsOverPrecisionValue() {
        assertThatThrownBy(() -> percentage("Promotion", "10.001"))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion discount value must have at most two decimal places.");
    }

    @Test
    void rejectsInvertedValidityPeriod() {
        assertThatThrownBy(() -> PromotionDefinition.create(
                "Promotion",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null,
                VALID_UNTIL,
                VALID_FROM))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Promotion start date must not be after its end date.");
    }

    private static PromotionDefinition percentage(
            String name,
            String value) {

        return PromotionDefinition.create(
                name,
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal(value),
                null,
                VALID_FROM,
                VALID_UNTIL);
    }

    private static PromotionDefinition fixedAmount(
            String value,
            String currency) {

        return PromotionDefinition.create(
                "Fixed Discount",
                null,
                DiscountType.FIXED_AMOUNT,
                new BigDecimal(value),
                currency,
                VALID_FROM,
                VALID_UNTIL);
    }
}
