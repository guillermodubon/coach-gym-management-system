package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary owned by the incident application layer. */
public interface IncidentStore {

    IncidentDetails report(
            IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    default IncidentDetails report(
            IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return report(definition, actor, occurredAt);
    }

    Optional<IncidentDetails> findById(UUID incidentId);

    default Optional<IncidentDetails> findById(UUID incidentId, UUID branchId) {
        return findById(incidentId);
    }

    IncidentPage findAll(IncidentSearchQuery query);

    default IncidentPage findAll(IncidentSearchQuery query, UUID branchId) {
        return findAll(query);
    }

    IncidentDetails transitionStatus(
            UUID incidentId,
            long expectedVersion,
            IncidentStatusTransition transition,
            String resolutionNotes,
            AuthenticatedActor actor,
            Instant occurredAt);

    IncidentDetails changePriority(
            UUID incidentId,
            long expectedVersion,
            IncidentPriority priority,
            AuthenticatedActor actor,
            Instant occurredAt);

    List<IncidentStatusHistoryDetails> findStatusHistory(
            UUID incidentId);

    default List<IncidentStatusHistoryDetails> findStatusHistory(
            UUID incidentId, UUID branchId) {
        return findStatusHistory(incidentId);
    }
}
