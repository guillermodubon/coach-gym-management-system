package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryPage;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategorySearchQuery;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryStore;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentSortDirection;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EquipmentCategoryPersistenceAdapter
        implements EquipmentCategoryStore {

    private final EquipmentCategoryJpaRepository repository;
    private final EntityManager entityManager;

    EquipmentCategoryPersistenceAdapter(
            EquipmentCategoryJpaRepository repository,
            EntityManager entityManager) {

        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public EquipmentCategoryDetails create(
            UUID id,
            EquipmentCategoryDefinition definition,
            Instant occurredAt) {

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        definition,
                        occurredAt);

        EquipmentCategoryJpaEntity saved =
                repository.saveAndFlush(entity);

        entityManager.refresh(saved);

        return saved.toDetails();
    }

    @Override
    @Transactional
    public EquipmentCategoryDetails update(
            UUID categoryId,
            EquipmentCategoryDefinition definition,
            long version,
            Instant occurredAt) {

        EquipmentCategoryJpaEntity entity =
                findEntityOrThrow(categoryId);

        ensureVersion(entity, version);

        entity.update(
                definition,
                occurredAt);

        return flush(entity).toDetails();
    }

    @Override
    @Transactional
    public EquipmentCategoryDetails activate(
            UUID categoryId,
            long version,
            Instant occurredAt) {

        EquipmentCategoryJpaEntity entity =
                findEntityOrThrow(categoryId);

        ensureVersion(entity, version);

        entity.activate(occurredAt);

        return flush(entity).toDetails();
    }

    @Override
    @Transactional
    public EquipmentCategoryDetails deactivate(
            UUID categoryId,
            long version,
            Instant occurredAt) {

        EquipmentCategoryJpaEntity entity =
                findEntityOrThrow(categoryId);

        ensureVersion(entity, version);

        entity.deactivate(occurredAt);

        return flush(entity).toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentCategoryDetails> findById(
            UUID categoryId) {

        return repository
                .findById(categoryId)
                .map(EquipmentCategoryJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentCategoryPage findAll(
            EquipmentCategorySearchQuery query) {

        Page<EquipmentCategoryJpaEntity> page =
                repository.search(
                        query.active(),
                        PageRequest.of(
                                query.page(),
                                query.size(),
                                toSort(query)));

        return new EquipmentCategoryPage(
                page.getContent().stream()
                        .map(EquipmentCategoryJpaEntity::toDetails)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNameIgnoreCase(
            String name,
            UUID excludeId) {

        return repository.existsByNameIgnoreCase(
                name,
                excludeId);
    }

    private EquipmentCategoryJpaEntity findEntityOrThrow(
            UUID categoryId) {

        return repository
                .findById(categoryId)
                .orElseThrow(() ->
                        new EquipmentCategoryNotFoundException(
                                categoryId));
    }

    private static void ensureVersion(
            EquipmentCategoryJpaEntity entity,
            long expectedVersion) {

        if (entity.version() != expectedVersion) {
            throw new EquipmentCategoryVersionConflictException(
                    entity.id());
        }
    }

    private EquipmentCategoryJpaEntity flush(
            EquipmentCategoryJpaEntity entity) {

        try {
            EquipmentCategoryJpaEntity saved =
                    repository.saveAndFlush(entity);

            entityManager.refresh(saved);

            return saved;
        } catch (OptimisticLockingFailureException
                 | OptimisticLockException exception) {

            throw new EquipmentCategoryVersionConflictException(
                    entity.id());
        }
    }

    private static Sort toSort(
            EquipmentCategorySearchQuery query) {

        String property = switch (query.sortField()) {
            case NAME -> "name";
            case ID -> "id";
        };

        Sort.Direction direction =
                query.direction()
                        == EquipmentSortDirection.ASC
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort primary =
                Sort.by(direction, property);

        if ("id".equals(property)) {
            return primary;
        }

        return primary.and(
                Sort.by(
                        Sort.Direction.ASC,
                        "id"));
    }
}