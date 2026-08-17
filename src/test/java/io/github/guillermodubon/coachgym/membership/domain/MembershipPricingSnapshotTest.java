package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPricingSnapshotTest {

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "16156f73-9cc1-45db-8d33-828d53c31f80");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "297f480c-b14b-428f-a0d4-ee22c79084c9");

    @Test
    void createsSnapshotWithoutPromotion() {
        MembershipPricingSnapshot snapshot =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        " PLAN-000001 ",
                        " Monthly Access ",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25"),
                        " usd ");

        assertThat(snapshot.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(snapshot.planCode())
                .isEqualTo("PLAN-000001");

        assertThat(snapshot.planName())
                .isEqualTo("Monthly Access");

        assertThat(snapshot.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(snapshot.currency())
                .isEqualTo("USD");

        assertThat(snapshot.promotion())
                .isNull();

        assertThat(snapshot.discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(snapshot.finalPrice())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void createsSnapshotWithPercentagePromotion() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000001",
                        "September Discount",
                        DiscountType.PERCENTAGE,
                        new BigDecimal("10"),
                        null);

        MembershipPricingSnapshot snapshot =
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

        assertThat(snapshot.promotion())
                .isEqualTo(promotion);

        assertThat(snapshot.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(snapshot.finalPrice())
                .isEqualByComparingTo("22.50");
    }

    @Test
    void createsSnapshotWithFixedPromotion() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000002",
                        "Five Dollars Off",
                        DiscountType.FIXED_AMOUNT,
                        new BigDecimal("5"),
                        " usd ");

        MembershipPricingSnapshot snapshot =
                new MembershipPricingSnapshot(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD",
                        promotion,
                        new BigDecimal("5.00"),
                        new BigDecimal("20.00"));

        assertThat(
                snapshot.promotion()
                        .promotionCurrency())
                .isEqualTo("USD");

        assertThat(snapshot.discountAmount())
                .isEqualByComparingTo("5.00");

        assertThat(snapshot.finalPrice())
                .isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsFinalPriceThatDoesNotMatchDiscount() {
        assertThatThrownBy(
                () ->
                        new MembershipPricingSnapshot(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal("25.00"),
                                "USD",
                                null,
                                new BigDecimal("0.00"),
                                new BigDecimal("20.00")))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership final price must equal "
                                + "the list price minus the "
                                + "discount amount.");
    }

    @Test
    void rejectsDiscountWithoutPromotion() {
        assertThatThrownBy(
                () ->
                        new MembershipPricingSnapshot(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal("25.00"),
                                "USD",
                                null,
                                new BigDecimal("5.00"),
                                new BigDecimal("20.00")))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership without a promotion "
                                + "must not contain a discount.");
    }

    @Test
    void rejectsPromotionWithoutPositiveDiscount() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000001",
                        "September Discount",
                        DiscountType.PERCENTAGE,
                        new BigDecimal("10"),
                        null);

        assertThatThrownBy(
                () ->
                        new MembershipPricingSnapshot(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal("25.00"),
                                "USD",
                                promotion,
                                BigDecimal.ZERO,
                                new BigDecimal("25.00")))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership with a promotion must "
                                + "contain a positive discount.");
    }

    @Test
    void rejectsDiscountGreaterThanListPrice() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000001",
                        "Invalid Discount",
                        DiscountType.FIXED_AMOUNT,
                        new BigDecimal("30.00"),
                        "USD");

        assertThatThrownBy(
                () ->
                        new MembershipPricingSnapshot(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal("25.00"),
                                "USD",
                                promotion,
                                new BigDecimal("30.00"),
                                BigDecimal.ZERO))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership discount amount must not "
                                + "exceed the list price.");
    }

    @Test
    void rejectsFixedPromotionCurrencyMismatch() {
        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        PROMOTION_ID,
                        "PROMO-000001",
                        "Five Dollars Off",
                        DiscountType.FIXED_AMOUNT,
                        new BigDecimal("5.00"),
                        "USD");

        assertThatThrownBy(
                () ->
                        new MembershipPricingSnapshot(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal("25.00"),
                                "EUR",
                                promotion,
                                new BigDecimal("5.00"),
                                new BigDecimal("20.00")))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Fixed promotion currency must match "
                                + "the membership plan currency.");
    }

    @Test
    void rejectsCurrencyOnPercentagePromotion() {
        assertThatThrownBy(
                () ->
                        new MembershipPromotionSnapshot(
                                PROMOTION_ID,
                                "PROMO-000001",
                                "Percentage Discount",
                                DiscountType.PERCENTAGE,
                                new BigDecimal("10.00"),
                                "USD"))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Percentage promotion currency "
                                + "must be absent.");
    }
}
