package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.application.*;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


@Repository
class PromotionPersistenceAdapter implements PromotionStore {

    private final PromotionJpaRepository promotionRepository;
    private final EntityManager entityManager;

    PromotionPersistenceAdapter(
            PromotionJpaRepository promotionRepository,
            EntityManager entityManager) {

        this.promotionRepository = promotionRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public PromotionDetails create(
            PromotionDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PromotionJpaEntity promotion =
                promotionRepository.saveAndFlush(
                        PromotionJpaEntity.create(
                                definition,
                                actor,
                                occurredAt));

        entityManager.refresh(promotion);

        return promotion.toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionPage findAll(
            PromotionSearchQuery query) {

        Page<PromotionJpaEntity> page =
                promotionRepository.search(
                        query.active(),
                        query.name(),
                        query.discountType(),
                        query.validOn() != null,
                        query.validOn(),
                        PageRequest.of(
                                query.page(),
                                query.size(),
                                toSort(query)));

        return new PromotionPage(
                page.getContent()
                        .stream()
                        .map(PromotionJpaEntity::toDetails)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromotionDetails> findById(UUID id) {
        return promotionRepository
                .findById(id)
                .map(PromotionJpaEntity::toDetails);
    }

    @Override
    @Transactional
    public PromotionDetails update(
            UUID id,
            PromotionDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PromotionJpaEntity promotion =
                requirePromotion(id);

        verifyVersion(
                id,
                expectedVersion,
                promotion.version());

        promotion.updateDefinition(
                definition,
                actor,
                occurredAt);

        PromotionJpaEntity saved =
                promotionRepository.saveAndFlush(promotion);

        return saved.toDetails();
    }

    @Override
    @Transactional
    public PromotionDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PromotionJpaEntity promotion =
                requirePromotion(id);

        verifyVersion(
                id,
                expectedVersion,
                promotion.version());

        promotion.changeActive(
                active,
                actor,
                occurredAt);

        PromotionJpaEntity saved =
                promotionRepository.saveAndFlush(promotion);

        return saved.toDetails();
    }

    private PromotionJpaEntity requirePromotion(
            UUID id) {

        return promotionRepository
                .findById(id)
                .orElseThrow(
                        () -> new PromotionNotFoundException(id));
    }

    private static void verifyVersion(
            UUID id,
            long expectedVersion,
            long currentVersion) {

        if (expectedVersion != currentVersion) {
            throw new PromotionVersionConflictException(
                    id,
                    expectedVersion,
                    currentVersion);
        }
    }

    private static Sort toSort(
            PromotionSearchQuery query) {

        String property = switch (query.sortField()) {
            case NAME -> "name";
            case VALID_FROM -> "validFrom";
            case VALID_UNTIL -> "validUntil";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };

        Sort.Direction direction =
                query.direction()
                        == PromotionSortDirection.ASC
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        return Sort.by(direction, property)
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}