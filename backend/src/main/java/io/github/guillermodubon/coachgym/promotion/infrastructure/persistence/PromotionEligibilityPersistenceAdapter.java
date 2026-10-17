package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import io.github.guillermodubon.coachgym.promotion.application.PromotionEligibilityStore;
import io.github.guillermodubon.coachgym.promotion.application.PromotionNotFoundException;
import io.github.guillermodubon.coachgym.promotion.application.PromotionVersionConflictException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PromotionEligibilityPersistenceAdapter
        implements PromotionEligibilityStore {

    private final PromotionJpaRepository promotionRepository;
    private final PromotionPlanEligibilityJpaRepository
            eligibilityRepository;

    PromotionEligibilityPersistenceAdapter(
            PromotionJpaRepository promotionRepository,
            PromotionPlanEligibilityJpaRepository
                    eligibilityRepository) {

        this.promotionRepository = promotionRepository;
        this.eligibilityRepository =
                eligibilityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findEligiblePlanIds(
            UUID promotionId) {

        return Set.copyOf(
                eligibilityRepository
                        .findPlanIdsByPromotionId(
                                promotionId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPlanEligible(
            UUID promotionId,
            UUID membershipPlanId) {

        return eligibilityRepository
                .existsByIdPromotionIdAndIdMembershipPlanId(
                        promotionId,
                        membershipPlanId);
    }

    @Override
    @Transactional
    public Set<UUID> replaceEligiblePlanIds(
            UUID promotionId,
            Set<UUID> planIds,
            long expectedPromotionVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PromotionJpaEntity promotion =
                promotionRepository.findById(
                                promotionId)
                        .orElseThrow(
                                () ->
                                        new PromotionNotFoundException(
                                                promotionId));

        if (promotion.version()
                != expectedPromotionVersion) {

            throw new PromotionVersionConflictException(
                    promotionId,
                    expectedPromotionVersion,
                    promotion.version());
        }

        eligibilityRepository.deleteByPromotionId(
                promotionId);

        eligibilityRepository.flush();

        if (!planIds.isEmpty()) {
            eligibilityRepository.saveAll(
                    planIds.stream()
                            .map(
                                    planId ->
                                            PromotionPlanEligibilityJpaEntity
                                                    .create(
                                                            promotionId,
                                                            planId,
                                                            occurredAt))
                            .collect(
                                    Collectors.toList()));

            eligibilityRepository.flush();
        }

        promotion.markEligibilityChanged(
                actor,
                occurredAt);

        promotionRepository.saveAndFlush(
                promotion);

        return Set.copyOf(planIds);
    }
}
