package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCancellation;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusTransition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for maintenance work orders and their status history. */
public interface MaintenanceStore {

    MaintenanceDetails schedule(
            MaintenanceDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    MaintenanceDetails updateScheduled(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceUpdateDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    Optional<MaintenanceDetails> findById(UUID maintenanceId);

    MaintenancePage findAll(MaintenanceSearchQuery query);

    MaintenanceDetails transitionStatus(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatusTransition transition,
            Instant startedAt,
            AuthenticatedActor actor,
            Instant occurredAt);

    MaintenanceDetails complete(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatusTransition transition,
            MaintenanceCompletion completion,
            AuthenticatedActor actor,
            Instant occurredAt);

    MaintenanceDetails cancel(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceStatus currentStatus,
            MaintenanceStatusTransition transition,
            MaintenanceCancellation cancellation,
            AuthenticatedActor actor,
            Instant occurredAt);

    List<MaintenanceStatusHistoryDetails> findStatusHistory(UUID maintenanceId);

    boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status);
}
