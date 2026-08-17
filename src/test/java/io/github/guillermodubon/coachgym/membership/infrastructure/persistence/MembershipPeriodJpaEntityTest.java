package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodDates;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPeriodJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "bc3489c7-c009-430d-a251-f7f59041d950");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "54e88b27-7fe7-4479-b8d2-0ad89d7c7544");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "d909313c-fafb-46cf-a46c-f4e4089b33bc");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "a240cadc-c215-485a-ae07-b3109db6d821");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "385e26e4-f4d6-41ae-b1d2-b72404cae8e3");

    private static final Instant NOW =
            Instant.parse("2026-08-16T20:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Test
    void mapsInitialPeriodWithoutPromotion() {
        MembershipCreation creation =
                new MembershipCreation(
                        CLIENT_ID,
                        dates(),
                        MembershipPricingSnapshot
                                .withoutPromotion(
                                        PLAN_ID,
                                        "PLAN-000001",
                                        "Monthly Access",
                                        1,
                                        DurationUnit.MONTH,
                                        new BigDecimal("25.00"),
                                        "USD"));

        MembershipPeriodJpaEntity entity =
                MembershipPeriodJpaEntity.initial(
                        MEMBERSHIP_ID,
                        creation,
                        ACTOR,
                        NOW);

        MembershipPeriodDetails details =
                entity.toDetails();

        assertThat(details.periodNumber())
                .isEqualTo((short) 1);

        assertThat(details.source())
                .isEqualTo(
                        MembershipPeriodSource.INITIAL);

        assertThat(details.pricing().promotion())
                .isNull();

        assertThat(details.pricing().discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(details.pricing().finalPrice())
                .isEqualByComparingTo("25.00");

        assertThat(details.startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 9, 1));

        assertThat(details.effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 1));
    }

    @Test
    void mapsInitialPeriodWithPromotion() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000001",
                        "September Discount",
                        DiscountType.PERCENTAGE,
                        new BigDecimal("10.00"),
                        null);

        MembershipPricingSnapshot pricing =
                new MembershipPricingSnapshot(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD",
                        promotion,
                        new BigDecimal("2.50"),
                        new BigDecimal("22.50"));

        MembershipCreation creation =
                new MembershipCreation(
                        CLIENT_ID,
                        dates(),
                        pricing);

        MembershipPeriodJpaEntity entity =
                MembershipPeriodJpaEntity.initial(
                        MEMBERSHIP_ID,
                        creation,
                        ACTOR,
                        NOW);

        MembershipPeriodDetails details =
                entity.toDetails();

        assertThat(details.pricing().promotion())
                .isNotNull();

        assertThat(
                details.pricing()
                        .promotion()
                        .promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(details.pricing().discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(details.pricing().finalPrice())
                .isEqualByComparingTo("22.50");
    }

    private static MembershipPeriodDates dates() {
        return new MembershipPeriodDates(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1));
    }
}
