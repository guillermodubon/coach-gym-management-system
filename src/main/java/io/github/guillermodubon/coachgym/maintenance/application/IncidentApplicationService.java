package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentOperations;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.application.command.ChangeIncidentPriorityCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ReportIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ResolveIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartIncidentInvestigationCommand;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusPolicy;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusTransition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
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

/** Application service for reporting, querying, and managing incidents. */
@Service
public class IncidentApplicationService {

    private final IncidentStore incidentStore;
    private final EquipmentLookup equipmentLookup;
    private final EquipmentIncidentOperations equipmentIncidentOperations;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final IncidentStatusPolicy statusPolicy;

    public IncidentApplicationService(
            IncidentStore incidentStore,
            EquipmentLookup equipmentLookup,
            EquipmentIncidentOperations equipmentIncidentOperations,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.incidentStore = incidentStore;
        this.equipmentLookup = equipmentLookup;
        this.equipmentIncidentOperations = equipmentIncidentOperations;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.statusPolicy = new IncidentStatusPolicy();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentDetails report(
            ReportIncidentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Report incident command is required.");
        validateActor(actor);

        EquipmentDetails equipment = equipmentLookup
                .findById(command.equipmentId())
                .orElseThrow(() -> new IncidentEquipmentUnavailableException(
                        command.equipmentId(),
                        "Equipment does not exist: " + command.equipmentId() + "."));

        if (equipment.status() == EquipmentStatus.RETIRED) {
            throw new IncidentEquipmentUnavailableException(
                    equipment.id(),
                    "Retired equipment cannot accept new incidents.");
        }

        Instant occurredAt = clock.instant();
        IncidentDetails reported = incidentStore.report(
                command.definition(), actor, occurredAt);

        boolean takenOutOfService = false;
        if (command.takeOutOfService()) {
            equipmentIncidentOperations.takeOutOfServiceForIncident(
                    equipment.id(),
                    reported.incidentCode(),
                    command.equipmentVersion(),
                    actor,
                    occurredAt);
            takenOutOfService = equipment.status() == EquipmentStatus.AVAILABLE;
        }

        eventPublisher.publishEvent(new IncidentReportedEvent(
                reported.id(),
                reported.incidentCode(),
                reported.equipmentId(),
                equipment.equipmentCode(),
                reported.priority(),
                takenOutOfService,
                actor.id(),
                actor.username(),
                occurredAt));

        return reported;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails startInvestigation(
            StartIncidentInvestigationCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Start investigation command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId());
        IncidentStatusTransition transition;
        try {
            transition = statusPolicy.startInvestigation(
                    current.status(), command.reason());
        } catch (IncidentValidationException exception) {
            throw new IncidentStateConflictException(
                    current.id(), exception.getMessage());
        }

        Instant occurredAt = clock.instant();
        IncidentDetails updated = incidentStore.transitionStatus(
                current.id(), command.version(), transition, null,
                actor, occurredAt);

        eventPublisher.publishEvent(new IncidentInvestigationStartedEvent(
                updated.id(),
                updated.incidentCode(),
                updated.equipmentId(),
                actor.id(),
                actor.username(),
                occurredAt));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails changePriority(
            ChangeIncidentPriorityCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Change priority command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId());
        if (current.priority() == command.priority()) {
            throw new IncidentStateConflictException(
                    current.id(), "Incident priority must change.");
        }

        Instant occurredAt = clock.instant();
        IncidentDetails updated = incidentStore.changePriority(
                current.id(), command.version(), command.priority(),
                actor, occurredAt);

        eventPublisher.publishEvent(new IncidentPriorityChangedEvent(
                updated.id(),
                updated.incidentCode(),
                current.priority(),
                updated.priority(),
                command.reason(),
                actor.id(),
                actor.username(),
                occurredAt));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails resolve(
            ResolveIncidentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Resolve incident command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId());
        IncidentStatusTransition transition;
        try {
            transition = statusPolicy.resolve(
                    current.status(), command.resolutionNotes());
        } catch (IncidentValidationException exception) {
            throw new IncidentStateConflictException(
                    current.id(), exception.getMessage());
        }

        Instant occurredAt = clock.instant();
        IncidentDetails updated = incidentStore.transitionStatus(
                current.id(), command.version(), transition,
                command.resolutionNotes(), actor, occurredAt);

        eventPublisher.publishEvent(new IncidentResolvedEvent(
                updated.id(),
                updated.incidentCode(),
                updated.equipmentId(),
                updated.resolutionNotes(),
                actor.id(),
                actor.username(),
                occurredAt));

        return updated;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentDetails findById(UUID incidentId) {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        return findRequired(incidentId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentPage findAll(IncidentSearchQuery query) {
        Objects.requireNonNull(query, "Incident search query is required.");
        return incidentStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<IncidentStatusHistoryDetails> findStatusHistory(
            UUID incidentId) {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        findRequired(incidentId);
        return incidentStore.findStatusHistory(incidentId);
    }

    private IncidentDetails findRequired(UUID incidentId) {
        return incidentStore.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private static void validateActor(AuthenticatedActor actor) {
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(actor.id(), "Authenticated actor id is required.");
        if (actor.username() == null || actor.username().isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated actor username is required.");
        }
    }
}
