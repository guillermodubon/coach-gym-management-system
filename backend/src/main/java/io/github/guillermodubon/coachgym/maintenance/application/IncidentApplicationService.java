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
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final BranchOperationContextResolver branchContextResolver;

    public IncidentApplicationService(
            IncidentStore incidentStore,
            EquipmentLookup equipmentLookup,
            EquipmentIncidentOperations equipmentIncidentOperations,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(incidentStore, equipmentLookup, equipmentIncidentOperations,
                eventPublisher, clock, null);
    }

    @Autowired
    public IncidentApplicationService(
            IncidentStore incidentStore,
            EquipmentLookup equipmentLookup,
            EquipmentIncidentOperations equipmentIncidentOperations,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.incidentStore = incidentStore;
        this.equipmentLookup = equipmentLookup;
        this.equipmentIncidentOperations = equipmentIncidentOperations;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.statusPolicy = new IncidentStatusPolicy();
        this.branchContextResolver = branchContextResolver;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentDetails report(
            ReportIncidentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Report incident command is required.");
        validateActor(actor);

        UUID branchId = branchId(actor);
        EquipmentDetails equipment = findEquipmentInBranch(command.equipmentId(), branchId)
                .orElseThrow(() -> new IncidentEquipmentUnavailableException(
                        command.equipmentId(),
                        "Equipment does not exist: " + command.equipmentId() + "."));

        if (equipment.status() == EquipmentStatus.RETIRED) {
            throw new IncidentEquipmentUnavailableException(
                    equipment.id(),
                    "Retired equipment cannot accept new incidents.");
        }

        Instant occurredAt = clock.instant();
        IncidentDetails reported = reportInBranch(
                command.definition(), actor, occurredAt, branchId);

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
                occurredAt,
                reported.branchId()));

        return reported;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails startInvestigation(
            StartIncidentInvestigationCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Start investigation command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId(), actor);
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
                occurredAt,
                updated.branchId()));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails changePriority(
            ChangeIncidentPriorityCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Change priority command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId(), actor);
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
                occurredAt,
                updated.branchId()));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetails resolve(
            ResolveIncidentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Resolve incident command is required.");
        validateActor(actor);

        IncidentDetails current = findRequired(command.incidentId(), actor);
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
                occurredAt,
                updated.branchId()));

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
    public IncidentDetails findByIdForActor(
            UUID incidentId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        return findRequired(incidentId, actor);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentPage findAll(IncidentSearchQuery query) {
        Objects.requireNonNull(query, "Incident search query is required.");
        return incidentStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentPage findAllForActor(
            IncidentSearchQuery query,
            AuthenticatedActor actor) {
        return findAllForActor(query, actor, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public IncidentPage findAllForActor(
            IncidentSearchQuery query,
            AuthenticatedActor actor,
            UUID requestedBranchId) {
        Objects.requireNonNull(query, "Incident search query is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (branchContextResolver == null) {
            throw new ActiveBranchContextUnavailableException();
        }
        UUID branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                branchContextResolver.resolveOperation(actor.id()), requestedBranchId);
        return incidentStore.findAll(query, branchId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<IncidentStatusHistoryDetails> findStatusHistory(
            UUID incidentId) {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        findRequired(incidentId);
        return incidentStore.findStatusHistory(incidentId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<IncidentStatusHistoryDetails> findStatusHistoryForActor(
            UUID incidentId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        findRequired(incidentId, actor);
        return findHistoryInBranch(incidentId, branchId(actor));
    }

    private IncidentDetails findRequired(UUID incidentId) {
        return incidentStore.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private IncidentDetails findRequired(
            UUID incidentId,
            AuthenticatedActor actor) {
        return findIncidentInBranch(incidentId, branchId(actor))
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private Optional<EquipmentDetails> findEquipmentInBranch(
            UUID equipmentId,
            UUID branchId) {
        return branchContextResolver == null
                ? equipmentLookup.findById(equipmentId)
                : equipmentLookup.findById(equipmentId, branchId);
    }

    private IncidentDetails reportInBranch(
            io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return branchContextResolver == null
                ? incidentStore.report(definition, actor, occurredAt)
                : incidentStore.report(definition, actor, occurredAt, branchId);
    }

    private Optional<IncidentDetails> findIncidentInBranch(
            UUID incidentId,
            UUID branchId) {
        return branchContextResolver == null
                ? incidentStore.findById(incidentId)
                : incidentStore.findById(incidentId, branchId);
    }

    private IncidentPage findAllInBranch(
            IncidentSearchQuery query,
            UUID branchId) {
        return branchContextResolver == null
                ? incidentStore.findAll(query)
                : incidentStore.findAll(query, branchId);
    }

    private List<IncidentStatusHistoryDetails> findHistoryInBranch(
            UUID incidentId,
            UUID branchId) {
        return branchContextResolver == null
                ? incidentStore.findStatusHistory(incidentId)
                : incidentStore.findStatusHistory(incidentId, branchId);
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(
                branchContextResolver.resolveOperation(actor.id()));
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
