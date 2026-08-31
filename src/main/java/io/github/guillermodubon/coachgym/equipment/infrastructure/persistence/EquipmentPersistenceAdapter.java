package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryStore;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentPage;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentSearchQuery;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentSortDirection;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentSortField;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentStore;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EquipmentPersistenceAdapter implements EquipmentStore, EquipmentLookup {

    private final EquipmentJpaRepository equipmentRepository;
    private final EquipmentStatusHistoryJpaRepository historyRepository;
    private final EquipmentCategoryStore categoryStore;
    private final EntityManager entityManager;

    EquipmentPersistenceAdapter(
            EquipmentJpaRepository equipmentRepository,
            EquipmentStatusHistoryJpaRepository historyRepository,
            EquipmentCategoryStore categoryStore,
            EntityManager entityManager) {
        this.equipmentRepository = equipmentRepository;
        this.historyRepository = historyRepository;
        this.categoryStore = categoryStore;
        this.entityManager = entityManager;
    }

    // ── EquipmentStore ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EquipmentDetails register(
            UUID id,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(id, definition, actor, occurredAt);
        EquipmentJpaEntity saved = equipmentRepository.saveAndFlush(entity);
        entityManager.refresh(saved);
        String categoryName = resolveCategoryName(saved.categoryId());
        return saved.toDetails(categoryName);
    }

    @Override
    @Transactional
    public EquipmentDetails update(
            UUID equipmentId,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            long version,
            Instant occurredAt) {
        EquipmentJpaEntity entity = findEntityOrThrow(equipmentId);
        ensureVersion(entity, version);
        entity.update(definition, actor, occurredAt);
        EquipmentJpaEntity saved = flush(entity);
        String categoryName = resolveCategoryName(saved.categoryId());
        return saved.toDetails(categoryName);
    }

    @Override
    @Transactional
    public EquipmentDetails applyTransition(
            UUID equipmentId,
            EquipmentStatusTransition transition,
            AuthenticatedActor actor,
            long version,
            Instant occurredAt) {
        EquipmentJpaEntity entity = findEntityOrThrow(equipmentId);
        ensureVersion(entity, version);

        EquipmentStatus previousPublic = entity.status();
        EquipmentStatus newPublic = toPublicStatus(transition.to());

        // 1. Update equipment status (includes retirement columns when RETIRED).
        entity.applyStatus(newPublic, transition.reason(), actor, occurredAt);
        EquipmentJpaEntity saved = flush(entity);

        // 2. Append history row atomically within the same transaction.
        EquipmentStatusHistoryJpaEntity history = EquipmentStatusHistoryJpaEntity.create(
                UUID.randomUUID(),
                saved.id(),
                previousPublic,
                newPublic,
                transition.reason(),
                actor,
                occurredAt);
        historyRepository.save(history);

        String categoryName = resolveCategoryName(saved.categoryId());
        return saved.toDetails(categoryName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentDetails> findById(
            UUID equipmentId) {

        return equipmentRepository
                .findById(equipmentId)
                .map(entity ->
                        entity.toDetails(
                                resolveCategoryName(
                                        entity.categoryId())));
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentPage findAll(EquipmentSearchQuery query) {
        Page<EquipmentJpaEntity> page = equipmentRepository.search(
                query.categoryId(),
                query.status() != null
                        ? io.github.guillermodubon.coachgym.equipment.EquipmentStatus.valueOf(
                                query.status().name())
                        : null,
                query.search() != null ? query.search() : "",
                query.location() != null ? query.location() : "",
                PageRequest.of(query.page(), query.size(), toSort(query)));

        List<EquipmentDetails> items = page.getContent().stream()
                .map(e -> e.toDetails(resolveCategoryName(e.categoryId())))
                .toList();
        return new EquipmentPage(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySerialNumberIgnoreCase(String serialNumber, UUID excludeId) {
        return equipmentRepository.existsBySerialNumberIgnoreCase(serialNumber, excludeId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private EquipmentJpaEntity findEntityOrThrow(UUID equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
    }

    private static void ensureVersion(EquipmentJpaEntity entity, long expectedVersion) {
        if (entity.version() != expectedVersion) {
            throw new EquipmentVersionConflictException(entity.id());
        }
    }

    private EquipmentJpaEntity flush(EquipmentJpaEntity entity) {
        try {
            EquipmentJpaEntity saved = equipmentRepository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new EquipmentVersionConflictException(entity.id());
        }
    }

    private String resolveCategoryName(UUID categoryId) {
        return categoryStore.findById(categoryId)
                .map(EquipmentCategoryDetails::name)
                .orElse(null);
    }

    private static io.github.guillermodubon.coachgym.equipment.EquipmentStatus toPublicStatus(
            io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus domainStatus) {
        return io.github.guillermodubon.coachgym.equipment.EquipmentStatus.valueOf(
                domainStatus.name());
    }

    private static Sort toSort(EquipmentSearchQuery query) {
        String primary = switch (query.sortField()) {
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case STATUS -> "status";
            case ID -> "id";
        };
        Sort.Direction dir = query.direction() == EquipmentSortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, primary);
        if ("id".equals(primary)) {
            return sort;
        }
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
