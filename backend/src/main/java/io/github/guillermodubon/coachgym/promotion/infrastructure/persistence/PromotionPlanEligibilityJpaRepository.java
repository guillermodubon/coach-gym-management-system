package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PromotionPlanEligibilityJpaRepository
        extends JpaRepository<
        PromotionPlanEligibilityJpaEntity,
        PromotionPlanEligibilityId> {

    @Query("""
            select eligibility.id.membershipPlanId
            from PromotionPlanEligibilityJpaEntity eligibility
            where eligibility.id.promotionId = :promotionId
            """)
    List<UUID> findPlanIdsByPromotionId(
            @Param("promotionId")
            UUID promotionId);

    @Modifying
    @Query("""
            delete from PromotionPlanEligibilityJpaEntity eligibility
            where eligibility.id.promotionId = :promotionId
            """)
    int deleteByPromotionId(
            @Param("promotionId")
            UUID promotionId);

    boolean existsByIdPromotionIdAndIdMembershipPlanId(
    UUID promotionId,
    UUID membershipPlanId);
}
