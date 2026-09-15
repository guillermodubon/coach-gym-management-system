package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentNotFoundException;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentPage;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSearchQuery;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSortDirection;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentStore;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** PostgreSQL/JPA implementation of the incident persistence boundary. */
@Component
class IncidentPersistenceAdapter implements IncidentStore {

    private final IncidentJpaRepository incidentRepository;
    private final IncidentStatusHistoryJpaRepository historyRepository;
    private final EquipmentLookup equipmentLookup;
    private final EntityManager entityManager;

    IncidentPersistenceAdapter(
            IncidentJpaRepository incidentRepository,
            IncidentStatusHistoryJpaRepository historyRepository,
            EquipmentLookup equipmentLookup,
            EntityManager entityManager) {
        this.incidentRepository = incidentRepository;
        this.historyRepository = historyRepository;
        this.equipmentLookup = equipmentLookup;
        this.entityManager = entityManager;
    }

    @Override
    public IncidentDetails report(
            IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        UUID id = UUID.randomUUID();
        IncidentJpaEntity entity = IncidentJpaEntity.report(
                id, definition, actor, occurredAt);
        IncidentJpaEntity saved = flush(entity, id, 0L);
        historyRepository.saveAndFlush(
                IncidentStatusHistoryJpaEntity.initial(
                        saved.id(), actor.id(), occurredAt));
        return toDetails(saved);
    }

    @Override
    public Optional<IncidentDetails> findById(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .map(this::toDetails);
    }

    @Override
    public IncidentPage findAll(IncidentSearchQuery query) {
        PageRequest pageRequest = PageRequest.of(
                query.page(), query.size(), toSort(query));
        var page = incidentRepository.findAll(toSpecification(query), pageRequest);
        return new IncidentPage(
                page.getContent().stream().map(this::toDetails).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public IncidentDetails transitionStatus(
            UUID incidentId,
            long expectedVersion,
            IncidentStatusTransition transition,
            String resolutionNotes,
            AuthenticatedActor actor,
            Instant occurredAt) {
        IncidentJpaEntity entity = findEntityOrThrow(incidentId);
        ensureVersion(entity, expectedVersion);
        entity.applyTransition(transition, resolutionNotes, actor, occurredAt);
        IncidentJpaEntity saved = flush(entity, incidentId, expectedVersion);
        historyRepository.saveAndFlush(
                IncidentStatusHistoryJpaEntity.transition(
                        incidentId,
                        transition.previousStatus(),
                        transition.resultingStatus(),
                        transition.reason(),
                        actor.id(),
                        occurredAt));
        return toDetails(saved);
    }

    @Override
    public IncidentDetails changePriority(
            UUID incidentId,
            long expectedVersion,
            IncidentPriority priority,
            AuthenticatedActor actor,
            Instant occurredAt) {
        IncidentJpaEntity entity = findEntityOrThrow(incidentId);
        ensureVersion(entity, expectedVersion);
        entity.changePriority(priority, occurredAt);
        return toDetails(flush(entity, incidentId, expectedVersion));
    }

    @Override
    public List<IncidentStatusHistoryDetails> findStatusHistory(
            UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }
        return historyRepository
                .findByIncidentIdOrderByOccurredAtAscIdAsc(incidentId)
                .stream()
                .map(IncidentPersistenceAdapter::toHistoryDetails)
                .toList();
    }

    private IncidentJpaEntity findEntityOrThrow(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private static void ensureVersion(
            IncidentJpaEntity entity,
            long expectedVersion) {
        if (entity.version() != expectedVersion) {
            throw new IncidentVersionConflictException(
                    entity.id(), expectedVersion);
        }
    }

    private IncidentJpaEntity flush(
            IncidentJpaEntity entity,
            UUID incidentId,
            long expectedVersion) {
        try {
            IncidentJpaEntity saved = incidentRepository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new IncidentVersionConflictException(
                    incidentId, expectedVersion);
        }
    }

    private IncidentDetails toDetails(IncidentJpaEntity entity) {
        EquipmentDetails equipment = equipmentLookup.findById(entity.equipmentId())
                .orElse(null);
        return new IncidentDetails(
                entity.id(),
                entity.incidentNumber() == null ? 0L : entity.incidentNumber(),
                entity.incidentCode(),
                entity.equipmentId(),
                equipment == null ? null : equipment.equipmentCode(),
                equipment == null ? null : equipment.name(),
                entity.status(),
                entity.priority(),
                entity.description(),
                entity.reportedAt(),
                entity.reportedByUserId(),
                entity.assignedToUserId(),
                entity.resolvedAt(),
                entity.resolvedByUserId(),
                entity.resolutionNotes(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.version());
    }

    private static IncidentStatusHistoryDetails toHistoryDetails(
            IncidentStatusHistoryJpaEntity entity) {
        return new IncidentStatusHistoryDetails(
                entity.id(), entity.incidentId(), entity.previousStatus(),
                entity.newStatus(), entity.reason(), entity.occurredAt(),
                entity.changedByUserId());
    }

    private static Specification<IncidentJpaEntity> toSpecification(
            IncidentSearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.equipmentId() != null) {
                predicates.add(builder.equal(
                        root.get("equipmentId"), query.equipmentId()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), query.priority()));
            }
            if (query.reportedFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("reportedAt"), query.reportedFrom()));
            }
            if (query.reportedUntil() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("reportedAt"), query.reportedUntil()));
            }
            if (query.reportedByUserId() != null) {
                predicates.add(builder.equal(
                        root.get("reportedByUserId"), query.reportedByUserId()));
            }
            if (query.resolvedByUserId() != null) {
                predicates.add(builder.equal(
                        root.get("resolvedByUserId"), query.resolvedByUserId()));
            }
            if (query.search() != null) {
                String pattern = "%" + query.search()
                        .toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("incidentCode")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort toSort(IncidentSearchQuery query) {
        String property = switch (query.sortField()) {
            case REPORTED_AT -> "reportedAt";
            case PRIORITY -> "priority";
            case STATUS -> "status";
            case INCIDENT_CODE -> "incidentCode";
            case UPDATED_AT -> "updatedAt";
            case ID -> "id";
        };
        Sort.Direction direction =
                query.direction() == IncidentSortDirection.ASC
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;
        Sort primary = Sort.by(direction, property);
        return "id".equals(property)
                ? primary
                : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
