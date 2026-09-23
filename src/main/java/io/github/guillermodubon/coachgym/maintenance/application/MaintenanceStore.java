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

    default MaintenanceDetails schedule(
            MaintenanceDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return schedule(definition, actor, occurredAt);
    }

    MaintenanceDetails updateScheduled(
            UUID maintenanceId,
            long expectedVersion,
            MaintenanceUpdateDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    Optional<MaintenanceDetails> findById(UUID maintenanceId);

    default Optional<MaintenanceDetails> findById(UUID maintenanceId, UUID branchId) {
        return findById(maintenanceId);
    }

    MaintenancePage findAll(MaintenanceSearchQuery query);

    default MaintenancePage findAll(MaintenanceSearchQuery query, UUID branchId) {
        return findAll(query);
    }

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

    default List<MaintenanceStatusHistoryDetails> findStatusHistory(
            UUID maintenanceId, UUID branchId) {
        return findStatusHistory(maintenanceId);
    }

    boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status);

    default boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status,
            UUID branchId) {
        return existsByEquipmentIdAndStatus(equipmentId, status);
    }
}
