package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceOperations;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentLookup;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceUpdatedEvent;
import io.github.guillermodubon.coachgym.maintenance.application.command.CancelMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.CompleteMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ScheduleMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.UpdateScheduledMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCancellation;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusPolicy;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application orchestration for maintenance work orders.
 */
@Service
public class MaintenanceApplicationService {

    private final MaintenanceStore maintenanceStore;
    private final EquipmentLookup equipmentLookup;
    private final IncidentLookup incidentLookup;
    private final EquipmentMaintenanceOperations equipmentOperations;
    private final MaintenanceStatusPolicy statusPolicy;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public MaintenanceApplicationService(
            MaintenanceStore maintenanceStore,
            EquipmentLookup equipmentLookup,
            IncidentLookup incidentLookup,
            EquipmentMaintenanceOperations equipmentOperations,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {

        this.maintenanceStore = Objects.requireNonNull(
                maintenanceStore,
                "Maintenance store is required.");
        this.equipmentLookup = Objects.requireNonNull(
                equipmentLookup,
                "Equipment lookup is required.");
        this.incidentLookup = Objects.requireNonNull(
                incidentLookup,
                "Incident lookup is required.");
        this.equipmentOperations = Objects.requireNonNull(
                equipmentOperations,
                "Equipment maintenance operations are required.");
        this.statusPolicy = new MaintenanceStatusPolicy();
        this.clock = Objects.requireNonNull(
                clock,
                "Application clock is required.");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Application event publisher is required.");
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MaintenanceDetails schedule(
            ScheduleMaintenanceCommand command,
            AuthenticatedActor actor) {

        Objects.requireNonNull(command, "Schedule command is required.");
        requireActor(actor);

        EquipmentDetails equipment = requireEquipment(command.equipmentId());
        requireNotRetired(equipment);
        validateIncidentLink(command.incidentId(), command.equipmentId());

        Instant occurredAt = now();
        MaintenanceDetails result = maintenanceStore.schedule(
                command.definition(), actor, occurredAt);

        eventPublisher.publishEvent(new MaintenanceScheduledEvent(
                result.id(),
                result.maintenanceCode(),
                result.equipmentId(),
                result.equipmentCode(),
                result.incidentId(),
                result.maintenanceType(),
                result.scheduledOn(),
                result.estimatedCost(),
                result.currency(),
                actor.id(),
                actor.username(),
                occurredAt));

        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MaintenanceDetails updateScheduled(
            UpdateScheduledMaintenanceCommand command,
            AuthenticatedActor actor) {

        Objects.requireNonNull(command, "Update command is required.");
        requireActor(actor);

        MaintenanceDetails current = requireMaintenance(command.maintenanceId());
        requireStatus(
                current,
                MaintenanceStatus.SCHEDULED,
                "Only scheduled maintenance can be updated.");

        Instant occurredAt = now();
        MaintenanceDetails result = maintenanceStore.updateScheduled(
                command.maintenanceId(),
                command.version(),
                command.definition(),
                actor,
                occurredAt);

        eventPublisher.publishEvent(new MaintenanceUpdatedEvent(
                result.id(),
                result.maintenanceCode(),
                result.equipmentId(),
                result.equipmentCode(),
                result.incidentId(),
                result.scheduledOn(),
                result.estimatedCost(),
                result.currency(),
                actor.id(),
                actor.username(),
                occurredAt));

        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MaintenanceDetails findById(UUID maintenanceId) {
        return requireMaintenance(maintenanceId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MaintenancePage findAll(MaintenanceSearchQuery query) {
        Objects.requireNonNull(query, "Maintenance search query is required.");
        return maintenanceStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<MaintenanceStatusHistoryDetails> findStatusHistory(
            UUID maintenanceId) {

        requireMaintenance(maintenanceId);
        return maintenanceStore.findStatusHistory(maintenanceId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MaintenanceDetails start(
            StartMaintenanceCommand command,
            AuthenticatedActor actor) {

        Objects.requireNonNull(command, "Start command is required.");
        requireActor(actor);

        MaintenanceDetails current = requireMaintenance(command.maintenanceId());
        requireStatus(
                current,
                MaintenanceStatus.SCHEDULED,
                "Only scheduled maintenance can be started.");

        if (maintenanceStore.existsByEquipmentIdAndStatus(
                current.equipmentId(), MaintenanceStatus.IN_PROGRESS)) {
            throw new MaintenanceActiveOrderConflictException(current.equipmentId());
        }

        MaintenanceStatusTransition transition = statusPolicy.start(
                current.status(), command.reason());

        equipmentOperations.startMaintenance(
                current.equipmentId(),
                maintenanceReference(current),
                command.equipmentVersion(),
                actor,
                command.startedAt());

        MaintenanceDetails result = maintenanceStore.transitionStatus(
                current.id(),
                command.maintenanceVersion(),
                transition,
                command.startedAt(),
                actor,
                command.startedAt());

        eventPublisher.publishEvent(new MaintenanceStartedEvent(
                result.id(),
                result.maintenanceCode(),
                result.equipmentId(),
                result.equipmentCode(),
                result.incidentId(),
                current.status(),
                result.status(),
                actor.id(),
                actor.username(),
                command.startedAt()));

        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MaintenanceDetails complete(
            CompleteMaintenanceCommand command,
            AuthenticatedActor actor) {

        Objects.requireNonNull(command, "Complete command is required.");
        requireActor(actor);

        MaintenanceDetails current = requireMaintenance(command.maintenanceId());
        requireStatus(
                current,
                MaintenanceStatus.IN_PROGRESS,
                "Only in-progress maintenance can be completed.");

        MaintenanceCompletion completion = command.completion();
        completion.validateAgainst(current.startedAt());
        completion.validateCurrency(current.currency());

        MaintenanceStatusTransition transition = statusPolicy.complete(
                current.status(), "Maintenance work completed.");

        equipmentOperations.completeMaintenance(
                current.equipmentId(),
                maintenanceReference(current),
                toEquipmentStatus(command.equipmentOutcome()),
                command.equipmentVersion(),
                actor,
                command.completedAt());

        MaintenanceDetails result = maintenanceStore.complete(
                current.id(),
                command.maintenanceVersion(),
                transition,
                completion,
                actor,
                command.completedAt());

        eventPublisher.publishEvent(new MaintenanceCompletedEvent(
                result.id(),
                result.maintenanceCode(),
                result.equipmentId(),
                result.equipmentCode(),
                result.incidentId(),
                current.status(),
                result.status(),
                command.equipmentOutcome(),
                result.actualCost(),
                result.currency(),
                actor.id(),
                actor.username(),
                command.completedAt()));

        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MaintenanceDetails cancel(
            CancelMaintenanceCommand command,
            AuthenticatedActor actor) {

        Objects.requireNonNull(command, "Cancel command is required.");
        requireActor(actor);

        MaintenanceDetails current = requireMaintenance(command.maintenanceId());
        MaintenanceStatus previousStatus = current.status();
        MaintenanceCancellation cancellation = command.cancellation();
        cancellation.validateFor(previousStatus);

        MaintenanceStatusTransition transition = statusPolicy.cancel(
                previousStatus, cancellation.reason());
        Instant occurredAt = now();

        if (previousStatus == MaintenanceStatus.IN_PROGRESS) {
            equipmentOperations.cancelInProgressMaintenance(
                    current.equipmentId(),
                    maintenanceReference(current),
                    toEquipmentStatus(command.equipmentOutcome()),
                    requiredEquipmentVersion(command),
                    actor,
                    occurredAt);
        }

        MaintenanceDetails result = maintenanceStore.cancel(
                current.id(),
                command.maintenanceVersion(),
                previousStatus,
                transition,
                cancellation,
                actor,
                occurredAt);

        eventPublisher.publishEvent(new MaintenanceCancelledEvent(
                result.id(),
                result.maintenanceCode(),
                result.equipmentId(),
                result.equipmentCode(),
                result.incidentId(),
                previousStatus,
                result.status(),
                command.equipmentOutcome(),
                actor.id(),
                actor.username(),
                occurredAt));

        return result;
    }

    private MaintenanceDetails requireMaintenance(UUID maintenanceId) {
        if (maintenanceId == null) {
            throw new IllegalArgumentException("Maintenance id is required.");
        }

        return maintenanceStore.findById(maintenanceId)
                .orElseThrow(() -> new MaintenanceNotFoundException(maintenanceId));
    }

    private EquipmentDetails requireEquipment(UUID equipmentId) {
        if (equipmentId == null) {
            throw new MaintenanceEquipmentUnavailableException(
                    null, "Equipment id is required.");
        }

        return equipmentLookup.findById(equipmentId)
                .orElseThrow(() -> new MaintenanceEquipmentUnavailableException(
                        equipmentId, "Equipment does not exist."));
    }

    private void validateIncidentLink(UUID incidentId, UUID equipmentId) {
        if (incidentId == null) {
            return;
        }

        IncidentDetails incident = incidentLookup.findById(incidentId)
                .orElseThrow(() -> new MaintenanceIncidentNotFoundException(incidentId));

        if (!equipmentId.equals(incident.equipmentId())) {
            throw new MaintenanceIncidentMismatchException(incidentId, equipmentId);
        }

        if (incident.status() == IncidentStatus.RESOLVED) {
            throw new MaintenanceStateConflictException(
                    null,
                    "Resolved incidents cannot receive new maintenance work orders.");
        }
    }

    private static void requireNotRetired(EquipmentDetails equipment) {
        if (equipment.status() == EquipmentStatus.RETIRED) {
            throw new MaintenanceEquipmentUnavailableException(
                    equipment.id(),
                    "Retired equipment cannot receive maintenance work orders.");
        }
    }

    private static void requireStatus(
            MaintenanceDetails details,
            MaintenanceStatus expected,
            String message) {

        if (details.status() != expected) {
            throw new MaintenanceStateConflictException(details.id(), message);
        }
    }

    private static EquipmentStatus toEquipmentStatus(
            EquipmentMaintenanceOutcome outcome) {

        Objects.requireNonNull(
                outcome,
                "Equipment maintenance outcome is required.");

        return switch (outcome) {
            case AVAILABLE -> EquipmentStatus.AVAILABLE;
            case OUT_OF_SERVICE -> EquipmentStatus.OUT_OF_SERVICE;
        };
    }

    private static long requiredEquipmentVersion(
            CancelMaintenanceCommand command) {

        Long equipmentVersion = command.equipmentVersion();

        if (equipmentVersion == null) {
            throw new IllegalArgumentException(
                    "Equipment version is required when cancelling "
                            + "in-progress maintenance.");
        }

        return equipmentVersion;
    }

    private static String maintenanceReference(MaintenanceDetails details) {
        if (details.maintenanceCode() != null
                && !details.maintenanceCode().isBlank()) {
            return details.maintenanceCode();
        }

        return details.id().toString();
    }

    private static void requireActor(AuthenticatedActor actor) {
        Objects.requireNonNull(actor, "Authenticated actor is required.");
    }

    private Instant now() {
        return clock.instant();
    }
}
