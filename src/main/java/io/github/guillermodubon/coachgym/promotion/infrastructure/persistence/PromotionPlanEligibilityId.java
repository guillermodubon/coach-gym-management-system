package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class PromotionPlanEligibilityId
        implements Serializable {

    @Column(name = "promotion_id")
    private UUID promotionId;

    @Column(name = "membership_plan_id")
    private UUID membershipPlanId;

    protected PromotionPlanEligibilityId() {
    }

    PromotionPlanEligibilityId(
            UUID promotionId,
            UUID membershipPlanId) {

        this.promotionId = promotionId;
        this.membershipPlanId = membershipPlanId;
    }

    UUID promotionId() {
        return promotionId;
    }

    UUID membershipPlanId() {
        return membershipPlanId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other
                instanceof PromotionPlanEligibilityId that)) {
            return false;
        }

        return Objects.equals(
                promotionId,
                that.promotionId)
                && Objects.equals(
                membershipPlanId,
                that.membershipPlanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                promotionId,
                membershipPlanId);
    }
}
