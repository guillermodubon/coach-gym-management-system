package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipRenewal;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        schema = "gym",
        name = "membership_periods")
class MembershipPeriodJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "membership_id",
            nullable = false)
    private UUID membershipId;

    @Column(
            name = "period_number",
            nullable = false)
    private short periodNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "period_source",
            nullable = false,
            length = 20)
    private MembershipPeriodSource periodSource;

    @Column(
            name = "membership_plan_id",
            nullable = false)
    private UUID membershipPlanId;

    @Column(
            name = "plan_code_snapshot",
            nullable = false,
            length = 32)
    private String planCodeSnapshot;

    @Column(
            name = "plan_name_snapshot",
            nullable = false,
            length = 160)
    private String planNameSnapshot;

    @Column(
            name = "duration_value_snapshot",
            nullable = false)
    private short durationValueSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "duration_unit_snapshot",
            nullable = false,
            length = 10)
    private DurationUnit durationUnitSnapshot;

    @Column(
            name = "list_price",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal listPrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            nullable = false,
            length = 3)
    private String currency;

    @Column(name = "promotion_id")
    private UUID promotionId;

    @Column(
            name = "promotion_code_snapshot",
            length = 32)
    private String promotionCodeSnapshot;

    @Column(
            name = "promotion_name_snapshot",
            length = 160)
    private String promotionNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "promotion_type_snapshot",
            length = 20)
    private DiscountType promotionTypeSnapshot;

    @Column(
            name = "promotion_value_snapshot",
            precision = 12,
            scale = 2)
    private BigDecimal promotionValueSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "promotion_currency_snapshot",
            length = 3)
    private String promotionCurrencySnapshot;

    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal discountAmount;

    @Column(
            name = "final_price",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal finalPrice;

    @Column(
            name = "starts_on",
            nullable = false)
    private LocalDate startsOn;

    @Column(
            name = "base_ends_on",
            nullable = false)
    private LocalDate baseEndsOn;

    @Column(
            name = "effective_ends_on",
            nullable = false)
    private LocalDate effectiveEndsOn;

    @Column(
            name = "created_by_user_id",
            nullable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(
            name = "created_at",
            nullable = false)
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MembershipPeriodJpaEntity() {
    }

    short periodNumber() {
        return periodNumber;
    }

    static MembershipPeriodJpaEntity initial(
            UUID membershipId,
            MembershipCreation creation,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipPricingSnapshot pricing =
                creation.pricing();

        MembershipPeriodJpaEntity period =
                new MembershipPeriodJpaEntity();

        period.id =
                UUID.randomUUID();

        period.membershipId =
                membershipId;

        period.periodNumber =
                1;

        period.periodSource =
                MembershipPeriodSource.INITIAL;

        period.membershipPlanId =
                pricing.membershipPlanId();

        period.planCodeSnapshot =
                pricing.planCode();

        period.planNameSnapshot =
                pricing.planName();

        period.durationValueSnapshot =
                (short) pricing.durationValue();

        period.durationUnitSnapshot =
                pricing.durationUnit();

        period.listPrice =
                pricing.listPrice();

        period.currency =
                pricing.currency();

        period.applyPromotion(
                pricing.promotion());

        period.discountAmount =
                pricing.discountAmount();

        period.finalPrice =
                pricing.finalPrice();

        period.startsOn =
                creation.dates().startsOn();

        period.baseEndsOn =
                creation.dates().baseEndsOn();

        period.effectiveEndsOn =
                creation.dates().effectiveEndsOn();

        period.createdByUserId =
                actor.id();

        period.updatedByUserId =
                actor.id();

        period.createdAt =
                occurredAt;

        period.updatedAt =
                occurredAt;

        return period;
    }

    static MembershipPeriodJpaEntity renewal(
            UUID membershipId,
            MembershipRenewal renewal,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipPricingSnapshot pricing =
                renewal.pricing();

        MembershipPeriodJpaEntity period =
                new MembershipPeriodJpaEntity();

        period.id =
                UUID.randomUUID();

        period.membershipId =
                membershipId;

        period.periodNumber =
                renewal.periodNumber();

        period.periodSource =
                MembershipPeriodSource.RENEWAL;

        period.membershipPlanId =
                pricing.membershipPlanId();

        period.planCodeSnapshot =
                pricing.planCode();

        period.planNameSnapshot =
                pricing.planName();

        period.durationValueSnapshot =
                (short) pricing.durationValue();

        period.durationUnitSnapshot =
                pricing.durationUnit();

        period.listPrice =
                pricing.listPrice();

        period.currency =
                pricing.currency();

        period.applyPromotion(
                pricing.promotion());

        period.discountAmount =
                pricing.discountAmount();

        period.finalPrice =
                pricing.finalPrice();

        period.startsOn =
                renewal.dates().startsOn();

        period.baseEndsOn =
                renewal.dates().baseEndsOn();

        period.effectiveEndsOn =
                renewal.dates().effectiveEndsOn();

        period.createdByUserId =
                actor.id();

        period.updatedByUserId =
                actor.id();

        period.createdAt =
                occurredAt;

        period.updatedAt =
                occurredAt;

        return period;
    }

    private void applyPromotion(
            MembershipPromotionSnapshot promotion) {

        if (promotion == null) {
            promotionId = null;
            promotionCodeSnapshot = null;
            promotionNameSnapshot = null;
            promotionTypeSnapshot = null;
            promotionValueSnapshot = null;
            promotionCurrencySnapshot = null;
            return;
        }

        promotionId =
                promotion.promotionId();

        promotionCodeSnapshot =
                promotion.promotionCode();

        promotionNameSnapshot =
                promotion.promotionName();

        promotionTypeSnapshot =
                promotion.discountType();

        promotionValueSnapshot =
                promotion.discountValue();

        promotionCurrencySnapshot =
                promotion.promotionCurrency();
    }

    MembershipPeriodDetails toDetails() {

        MembershipPromotionSnapshot promotion =
                toPromotionSnapshot();

        MembershipPricingSnapshot pricing =
                new MembershipPricingSnapshot(
                        membershipPlanId,
                        planCodeSnapshot,
                        planNameSnapshot,
                        durationValueSnapshot,
                        durationUnitSnapshot,
                        listPrice,
                        normalizeCurrency(currency),
                        promotion,
                        discountAmount,
                        finalPrice);

        return new MembershipPeriodDetails(
                id,
                periodNumber,
                periodSource,
                pricing,
                startsOn,
                baseEndsOn,
                effectiveEndsOn,
                createdAt,
                version);
    }

    private MembershipPromotionSnapshot
    toPromotionSnapshot() {

        if (promotionId == null) {
            return null;
        }

        return new MembershipPromotionSnapshot(
                promotionId,
                promotionCodeSnapshot,
                promotionNameSnapshot,
                promotionTypeSnapshot,
                promotionValueSnapshot,
                normalizeCurrency(
                        promotionCurrencySnapshot));
    }

    private static String normalizeCurrency(
            String value) {

        return value == null
                ? null
                : value.trim();
    }

    UUID id() {
        return id;
    }

    UUID membershipId() {
        return membershipId;
    }

    BigDecimal finalPrice() {
        return finalPrice;
    }

    String currency() {
        return currency == null ? null : currency.trim();
    }
}
