package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceNotFoundException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceOperations;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceStateConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates equipment lifecycle changes owned by maintenance work orders. */
@Service
public class EquipmentMaintenanceApplicationService
        implements EquipmentMaintenanceOperations {

    private final EquipmentStore equipmentStore;
    private final ApplicationEventPublisher eventPublisher;

    public EquipmentMaintenanceApplicationService(
            EquipmentStore equipmentStore,
            ApplicationEventPublisher eventPublisher) {
        this.equipmentStore = equipmentStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails startMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        validateCommon(equipmentId, maintenanceReference, expectedVersion, actor, occurredAt);
        EquipmentDetails current = findRequired(equipmentId);
        if (current.status() == EquipmentStatus.RETIRED) {
            throw stateConflict(equipmentId,
                    "Retired equipment cannot enter maintenance.");
        }
        if (current.status() == EquipmentStatus.MAINTENANCE) {
            throw stateConflict(equipmentId,
                    "Equipment is already in maintenance.");
        }
        if (current.status() != EquipmentStatus.AVAILABLE
                && current.status() != EquipmentStatus.OUT_OF_SERVICE) {
            throw stateConflict(equipmentId,
                    "Equipment cannot enter maintenance from "
                            + current.status() + ".");
        }
        return apply(current, EquipmentStatus.MAINTENANCE,
                "Maintenance work started: " + maintenanceReference + ".",
                expectedVersion, actor, occurredAt);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails completeMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            EquipmentStatus resultingStatus,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        validateCommon(equipmentId, maintenanceReference, expectedVersion, actor, occurredAt);
        validateOutcome(resultingStatus);
        EquipmentDetails current = findRequired(equipmentId);
        requireMaintenance(current);
        return apply(current, resultingStatus,
                "Maintenance work completed: " + maintenanceReference + ".",
                expectedVersion, actor, occurredAt);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails cancelInProgressMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            EquipmentStatus resultingStatus,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        validateCommon(equipmentId, maintenanceReference, expectedVersion, actor, occurredAt);
        validateOutcome(resultingStatus);
        EquipmentDetails current = findRequired(equipmentId);
        requireMaintenance(current);
        return apply(current, resultingStatus,
                "In-progress maintenance cancelled: " + maintenanceReference + ".",
                expectedVersion, actor, occurredAt);
    }

    private EquipmentDetails findRequired(UUID equipmentId) {
        return equipmentStore.findById(equipmentId)
                .orElseThrow(() -> new EquipmentMaintenanceNotFoundException(equipmentId));
    }

    private EquipmentDetails apply(
            EquipmentDetails current,
            EquipmentStatus target,
            String reason,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        EquipmentStatusTransition transition =
                new EquipmentStatusTransition(
                        toDomainStatus(current.status()),
                        toDomainStatus(target),
                        reason);

        EquipmentDetails updated;
        try {
            updated = equipmentStore.applyTransition(
                    current.id(), transition, actor, expectedVersion, occurredAt);
        } catch (EquipmentVersionConflictException exception) {
            throw new EquipmentMaintenanceVersionConflictException(current.id());
        }
        eventPublisher.publishEvent(new EquipmentStatusChangedEvent(
                updated.id(), updated.equipmentCode(),
                current.status(), updated.status(), reason,
                actor.id(), actor.username(), occurredAt));
        return updated;
    }

    private static void requireMaintenance(EquipmentDetails current) {
        if (current.status() != EquipmentStatus.MAINTENANCE) {
            throw stateConflict(current.id(),
                    "Equipment must be in maintenance before the operation.");
        }
    }

    private static void validateOutcome(EquipmentStatus status) {
        if (status != EquipmentStatus.AVAILABLE
                && status != EquipmentStatus.OUT_OF_SERVICE) {
            throw new IllegalArgumentException(
                    "Maintenance outcome must be AVAILABLE or OUT_OF_SERVICE.");
        }
    }

    private static void validateCommon(
            UUID equipmentId,
            String reference,
            long version,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Maintenance reference is required.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Equipment version must not be negative.");
        }
    }

    private static EquipmentMaintenanceStateConflictException stateConflict(
            UUID equipmentId,
            String message) {
        return new EquipmentMaintenanceStateConflictException(equipmentId, message);
    }

    private static
    io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus
    toDomainStatus(EquipmentStatus status) {

        return io.github.guillermodubon.coachgym.equipment.domain
                .EquipmentStatus
                .valueOf(status.name());
    }
}
