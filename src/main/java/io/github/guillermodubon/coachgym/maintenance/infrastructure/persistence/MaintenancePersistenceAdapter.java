package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentLookup;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceEquipmentUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceIncidentNotFoundException;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceNotFoundException;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenancePage;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceSearchQuery;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceSortDirection;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceSortField;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceStore;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCancellation;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusTransition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class MaintenancePersistenceAdapter implements MaintenanceStore {

    private final MaintenanceJpaRepository maintenanceRepository;
    private final MaintenanceStatusHistoryJpaRepository historyRepository;
    private final EquipmentLookup equipmentLookup;
    private final IncidentLookup incidentLookup;
    private final EntityManager entityManager;

    MaintenancePersistenceAdapter(
            MaintenanceJpaRepository maintenanceRepository,
            MaintenanceStatusHistoryJpaRepository historyRepository,
            EquipmentLookup equipmentLookup,
            IncidentLookup incidentLookup,
            EntityManager entityManager) {
        this.maintenanceRepository = maintenanceRepository;
        this.historyRepository = historyRepository;
        this.equipmentLookup = equipmentLookup;
        this.incidentLookup = incidentLookup;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public MaintenanceDetails schedule(
            MaintenanceDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceJpaEntity entity = MaintenanceJpaEntity.schedule(
                definition, actor, occurredAt);
        MaintenanceJpaEntity saved = maintenanceRepository.saveAndFlush(entity);
        entityManager.refresh(saved);
        historyRepository.saveAndFlush(
                MaintenanceStatusHistoryJpaEntity.initial(
                        saved.id(), actor, occurredAt));
        return toDetails(saved);
    }

    @Override
    @Transactional
    public MaintenanceDetails updateScheduled(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceUpdateDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceJpaEntity entity = findEntity(maintenanceId);
        entity.updateScheduled(expectedVersion, definition, occurredAt);
        return toDetails(save(entity, expectedVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceDetails> findById(UUID maintenanceId) {
        return maintenanceRepository.findById(maintenanceId)
                .map(this::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenancePage findAll(MaintenanceSearchQuery query) {
        Pageable pageable = PageRequest.of(
                query.page(), query.size(), sort(query));
        Page<MaintenanceJpaEntity> result = maintenanceRepository.findAll(
                specification(query), pageable);
        return new MaintenancePage(
                result.getContent().stream().map(this::toDetails).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional
    public MaintenanceDetails transitionStatus(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatusTransition transition,
            Instant startedAt,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceJpaEntity entity = findEntity(maintenanceId);
        entity.transition(expectedVersion, transition, startedAt, occurredAt);
        MaintenanceJpaEntity saved = save(entity, expectedVersion);
        saveHistory(saved.id(), transition, actor, occurredAt);
        return toDetails(saved);
    }

    @Override
    @Transactional
    public MaintenanceDetails complete(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatusTransition transition,
            MaintenanceCompletion completion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceJpaEntity entity = findEntity(maintenanceId);
        entity.complete(
                expectedVersion, transition, completion, actor, occurredAt);
        MaintenanceJpaEntity saved = save(entity, expectedVersion);
        saveHistory(saved.id(), transition, actor, occurredAt);
        return toDetails(saved);
    }

    @Override
    @Transactional
    public MaintenanceDetails cancel(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatus currentStatus,
            MaintenanceStatusTransition transition,
            MaintenanceCancellation cancellation,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceJpaEntity entity = findEntity(maintenanceId);
        entity.cancel(
                expectedVersion, currentStatus, transition,
                cancellation, occurredAt);
        MaintenanceJpaEntity saved = save(entity, expectedVersion);
        saveHistory(saved.id(), transition, actor, occurredAt);
        return toDetails(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceStatusHistoryDetails> findStatusHistory(
            UUID maintenanceId) {
        return historyRepository
                .findByMaintenanceIdOrderByOccurredAtAscIdAsc(maintenanceId)
                .stream()
                .map(MaintenanceStatusHistoryJpaEntity::toDetails)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status) {
        return maintenanceRepository.existsByEquipmentIdAndStatus(
                equipmentId, status);
    }

    private MaintenanceJpaEntity findEntity(UUID maintenanceId) {
        return maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new MaintenanceNotFoundException(maintenanceId));
    }

    private MaintenanceJpaEntity save(
            MaintenanceJpaEntity entity,
            long expectedVersion) {
        try {
            return maintenanceRepository.saveAndFlush(entity);
        } catch (OptimisticLockException
                | OptimisticLockingFailureException exception) {
            throw new MaintenanceVersionConflictException(
                    entity.id(), expectedVersion);
        }
    }

    private void saveHistory(
            UUID maintenanceId,
            MaintenanceStatusTransition transition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        historyRepository.saveAndFlush(
                MaintenanceStatusHistoryJpaEntity.transition(
                        maintenanceId, transition, actor, occurredAt));
    }

    private MaintenanceDetails toDetails(MaintenanceJpaEntity entity) {
        EquipmentDetails equipment = equipmentLookup.findById(entity.equipmentId())
                .orElseThrow(() -> new MaintenanceEquipmentUnavailableException(
                        entity.equipmentId(),
                        "Equipment not found for maintenance work order."));

        String incidentCode = null;
        if (entity.incidentId() != null) {
            IncidentDetails incident = incidentLookup.findById(entity.incidentId())
                    .orElseThrow(() -> new MaintenanceIncidentNotFoundException(
                            entity.incidentId()));
            incidentCode = incident.incidentCode();
        }

        return entity.toDetails(
                equipment.equipmentCode(),
                equipment.name(),
                incidentCode);
    }

    private static Specification<MaintenanceJpaEntity> specification(
            MaintenanceSearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.equipmentId() != null) {
                predicates.add(builder.equal(
                        root.get("equipmentId"), query.equipmentId()));
            }
            if (query.incidentId() != null) {
                predicates.add(builder.equal(
                        root.get("incidentId"), query.incidentId()));
            }
            if (query.maintenanceType() != null) {
                predicates.add(builder.equal(
                        root.get("maintenanceType"), query.maintenanceType()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.scheduledFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("scheduledOn"), query.scheduledFrom()));
            }
            if (query.scheduledUntil() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("scheduledOn"), query.scheduledUntil()));
            }
            if (query.createdByUserId() != null) {
                predicates.add(builder.equal(
                        root.get("createdByUserId"), query.createdByUserId()));
            }
            if (query.assignedToUserId() != null) {
                predicates.add(builder.equal(
                        root.get("assignedToUserId"), query.assignedToUserId()));
            }
            if (query.providerName() != null) {
                predicates.add(builder.like(
                        builder.lower(root.get("providerName")),
                        "%" + query.providerName().toLowerCase() + "%"));
            }
            if (query.search() != null) {
                String pattern = "%" + query.search().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("maintenanceCode")), pattern),
                        builder.like(builder.lower(root.get("providerName")), pattern),
                        builder.like(builder.lower(root.get("technicianName")), pattern),
                        builder.like(builder.lower(root.get("notes")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort sort(MaintenanceSearchQuery query) {
        Sort.Direction direction = query.direction()
                == MaintenanceSortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (query.sortField()) {
            case SCHEDULED_ON -> "scheduledOn";
            case STATUS -> "status";
            case MAINTENANCE_TYPE -> "maintenanceType";
            case MAINTENANCE_CODE -> "maintenanceCode";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
            case ID -> "id";
        };
        Sort primary = Sort.by(direction, property);
        if (query.sortField() == MaintenanceSortField.ID) {
            return primary;
        }
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
