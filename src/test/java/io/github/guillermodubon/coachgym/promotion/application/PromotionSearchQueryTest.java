package io.github.guillermodubon.coachgym.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PromotionSearchQueryTest {

    @Test
    void createsQueryWithDefaults() {
        PromotionSearchQuery query =
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        0,
                        25,
                        null,
                        null);

        assertThat(query.active()).isNull();
        assertThat(query.name()).isEmpty();
        assertThat(query.discountType()).isNull();
        assertThat(query.validOn()).isNull();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sortField())
                .isEqualTo(PromotionSortField.NAME);
        assertThat(query.direction())
                .isEqualTo(PromotionSortDirection.ASC);
    }

    @Test
    void normalizesNameAndSortingValues() {
        PromotionSearchQuery query =
                PromotionSearchQuery.from(
                        true,
                        "  September  ",
                        DiscountType.PERCENTAGE,
                        LocalDate.of(2026, 9, 15),
                        1,
                        10,
                        "valid_until",
                        "desc");

        assertThat(query.active()).isTrue();
        assertThat(query.name())
                .isEqualTo("September");
        assertThat(query.discountType())
                .isEqualTo(DiscountType.PERCENTAGE);
        assertThat(query.validOn())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.sortField())
                .isEqualTo(
                        PromotionSortField.VALID_UNTIL);
        assertThat(query.direction())
                .isEqualTo(
                        PromotionSortDirection.DESC);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() ->
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        -1,
                        25,
                        "name",
                        "asc"))
                .isInstanceOf(
                        PromotionValidationException.class)
                .hasMessage(
                        "Page must not be negative.");
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() ->
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        "name",
                        "asc"))
                .isInstanceOf(
                        PromotionValidationException.class)
                .hasMessage(
                        "Page size must be between 1 and 100.");

        assertThatThrownBy(() ->
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        0,
                        101,
                        "name",
                        "asc"))
                .isInstanceOf(
                        PromotionValidationException.class)
                .hasMessage(
                        "Page size must be between 1 and 100.");
    }

    @Test
    void rejectsUnsupportedSortField() {
        assertThatThrownBy(() ->
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        0,
                        25,
                        "discount_value",
                        "asc"))
                .isInstanceOf(
                        PromotionValidationException.class)
                .hasMessage(
                        "Unsupported promotion sort field.");
    }

    @Test
    void rejectsUnsupportedSortDirection() {
        assertThatThrownBy(() ->
                PromotionSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        0,
                        25,
                        "name",
                        "sideways"))
                .isInstanceOf(
                        PromotionValidationException.class)
                .hasMessage(
                        "Unsupported promotion sort direction.");
    }
}
