package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "promotion_plan_eligibility")
class PromotionPlanEligibilityJpaEntity {

    @EmbeddedId
    private PromotionPlanEligibilityId id;

    @Column(
            name = "created_at",
            nullable = false)
    private Instant createdAt;

    protected PromotionPlanEligibilityJpaEntity() {
    }

    private PromotionPlanEligibilityJpaEntity(
            PromotionPlanEligibilityId id,
            Instant createdAt) {

        this.id = id;
        this.createdAt = createdAt;
    }

    static PromotionPlanEligibilityJpaEntity create(
            UUID promotionId,
            UUID membershipPlanId,
            Instant occurredAt) {

        return new PromotionPlanEligibilityJpaEntity(
                new PromotionPlanEligibilityId(
                        promotionId,
                        membershipPlanId),
                occurredAt);
    }
}
