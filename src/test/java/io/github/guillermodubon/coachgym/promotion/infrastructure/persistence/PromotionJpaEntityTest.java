package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionJpaEntityTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-08-15T04:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    UUID.fromString(
                            "2d903ea1-b5e6-4506-b72e-7ab905ec5fa6"),
                    "coach-admin");

    @Test
    void createsEntityFromValidatedDefinition() {
        PromotionDefinition definition = PromotionDefinition.create(
                "September Discount",
                "Five dollars off selected plans.",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("5.00"),
                "USD",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30));

        PromotionJpaEntity entity =
                PromotionJpaEntity.create(
                        definition,
                        ACTOR,
                        OCCURRED_AT);

        PromotionDetails details = entity.toDetails();

        assertThat(details.id()).isNotNull();
        assertThat(details.promotionCode()).isNull();
        assertThat(details.name()).isEqualTo("September Discount");
        assertThat(details.description())
                .isEqualTo("Five dollars off selected plans.");
        assertThat(details.discountType())
                .isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(details.discountValue())
                .isEqualByComparingTo("5.00");
        assertThat(details.currency()).isEqualTo("USD");
        assertThat(details.validFrom())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(details.validUntil())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(details.active()).isTrue();
        assertThat(details.createdAt()).isEqualTo(OCCURRED_AT);
        assertThat(details.updatedAt()).isEqualTo(OCCURRED_AT);
        assertThat(details.version()).isZero();
    }

    @Test
    void preservesNullCurrencyForPercentagePromotion() {
        PromotionDefinition definition = PromotionDefinition.create(
                "Percentage Discount",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("20.00"),
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30));

        PromotionDetails details =
                PromotionJpaEntity.create(
                                definition,
                                ACTOR,
                                OCCURRED_AT)
                        .toDetails();

        assertThat(details.currency()).isNull();
    }
}
