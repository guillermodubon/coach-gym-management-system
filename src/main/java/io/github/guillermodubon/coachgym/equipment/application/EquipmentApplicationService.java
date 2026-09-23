package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkAvailableCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkOutOfServiceCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RegisterEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RetireEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateSerialNumberException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryInactiveException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusPolicy;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for equipment registration and query operations.
 *
 * <p>Blocks 5 and 6 extend this service with update and status-transition
 * operations. This block covers only create, findById, and findAll.
 */
@Service
public class EquipmentApplicationService {

    private final EquipmentStore equipmentStore;
    private final EquipmentCategoryStore categoryStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BranchOperationContextResolver branchContextResolver;

    public EquipmentApplicationService(
            EquipmentStore equipmentStore,
            EquipmentCategoryStore categoryStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(equipmentStore, categoryStore, eventPublisher, clock, null);
    }

    @Autowired
    public EquipmentApplicationService(
            EquipmentStore equipmentStore,
            EquipmentCategoryStore categoryStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.equipmentStore = equipmentStore;
        this.categoryStore = categoryStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.branchContextResolver = branchContextResolver;
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails register(RegisterEquipmentCommand command, AuthenticatedActor actor) {
        EquipmentDefinition definition = command.definition();
        UUID categoryId = definition.categoryId();

        // Category must exist and be active.
        var category = categoryStore.findById(categoryId)
                .orElseThrow(() -> new EquipmentCategoryNotFoundException(categoryId));
        if (!category.active()) {
            throw new EquipmentCategoryInactiveException(categoryId);
        }

        // Serial number must be globally unique (case-insensitive) if provided.
        UUID branchId = branchId(actor);
        if (definition.serialNumber() != null &&
                serialExistsInBranch(definition.serialNumber(), null, branchId)) {
            throw new DuplicateSerialNumberException(definition.serialNumber());
        }

        UUID id = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        EquipmentDetails registered = registerInBranch(
                id, definition, actor, occurredAt, branchId);

        eventPublisher.publishEvent(new EquipmentRegisteredEvent(
                registered.id(),
                registered.equipmentCode(),
                registered.categoryId(),
                actor.id(),
                actor.username(),
                occurredAt,
                registered.branchId()));

        return registered;
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails update(UpdateEquipmentCommand command, AuthenticatedActor actor) {
        UUID equipmentId = command.equipmentId();
        EquipmentDefinition definition = command.definition();

        // Equipment must exist (provides early 404 before version check).
        UUID branchId = branchId(actor);
        findEquipmentInBranch(equipmentId, branchId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));

        // New category must exist and be active.
        UUID categoryId = definition.categoryId();
        var category = categoryStore.findById(categoryId)
                .orElseThrow(() -> new EquipmentCategoryNotFoundException(categoryId));
        if (!category.active()) {
            throw new EquipmentCategoryInactiveException(categoryId);
        }

        // Serial number must be unique excluding the equipment being updated.
        if (definition.serialNumber() != null &&
                serialExistsInBranch(definition.serialNumber(), equipmentId, branchId)) {
            throw new DuplicateSerialNumberException(definition.serialNumber());
        }

        Instant occurredAt = clock.instant();
        EquipmentDetails updated = equipmentStore.update(
                equipmentId, definition, actor, command.version(), occurredAt);

        eventPublisher.publishEvent(new EquipmentUpdatedEvent(
                updated.id(),
                updated.equipmentCode(),
                actor.id(),
                actor.username(),
                occurredAt,
                updated.branchId()));

        return updated;
    }

    // ── markOutOfService ──────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails markOutOfService(MarkOutOfServiceCommand command, AuthenticatedActor actor) {
        return applyLifecycleTransition(
                command.equipmentId(),
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.OUT_OF_SERVICE,
                command.reason(),
                command.version(),
                actor);
    }

    // ── markAvailable ─────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails markAvailable(MarkAvailableCommand command, AuthenticatedActor actor) {
        return applyLifecycleTransition(
                command.equipmentId(),
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.AVAILABLE,
                command.reason(),
                command.version(),
                actor);
    }

    // ── retire ────────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails retire(RetireEquipmentCommand command, AuthenticatedActor actor) {
        return applyLifecycleTransition(
                command.equipmentId(),
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.RETIRED,
                command.reason(),
                command.version(),
                actor);
    }

    // ── shared lifecycle helper ───────────────────────────────────────────────

    private EquipmentDetails applyLifecycleTransition(
            UUID equipmentId,
            io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus target,
            String reason,
            long version,
            AuthenticatedActor actor) {

        EquipmentDetails current = findEquipmentInBranch(equipmentId, branchId(actor))
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));

        // Map public status → domain status for policy validation.
        io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus currentDomain =
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.valueOf(
                        current.status().name());

        EquipmentStatusTransition transition;
        try {
            transition = EquipmentStatusPolicy.validate(currentDomain, target, reason);
        } catch (EquipmentValidationException ex) {
            throw new EquipmentStateConflictException(currentDomain, target, ex.getMessage());
        }

        Instant occurredAt = clock.instant();
        EquipmentDetails updated = equipmentStore.applyTransition(
                equipmentId, transition, actor, version, occurredAt);

        EquipmentStatus previousPublic = EquipmentStatus.valueOf(currentDomain.name());
        EquipmentStatus newPublic = EquipmentStatus.valueOf(target.name());

        eventPublisher.publishEvent(new EquipmentStatusChangedEvent(
                updated.id(),
                updated.equipmentCode(),
                previousPublic,
                newPublic,
                transition.reason(),
                actor.id(),
                actor.username(),
                occurredAt,
                updated.branchId()));

        return updated;
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentDetails findById(UUID equipmentId) {
        return equipmentStore.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentDetails findByIdForActor(
            UUID equipmentId,
            AuthenticatedActor actor) {
        return findEquipmentInBranch(equipmentId, branchId(actor))
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentPage findAll(EquipmentSearchQuery query) {
        return equipmentStore.findAll(query);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentPage findAllForActor(
            EquipmentSearchQuery query,
            AuthenticatedActor actor) {
        return findAllForActor(query, actor, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentPage findAllForActor(
            EquipmentSearchQuery query,
            AuthenticatedActor actor,
            UUID requestedBranchId) {
        Objects.requireNonNull(query, "Equipment search query is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (branchContextResolver == null) {
            throw new ActiveBranchContextUnavailableException();
        }
        UUID branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                branchContextResolver.resolveOperation(actor.id()), requestedBranchId);
        return equipmentStore.findAll(query, branchId);
    }

    private Optional<EquipmentDetails> findEquipmentInBranch(
            UUID equipmentId,
            UUID branchId) {
        return branchContextResolver == null
                ? equipmentStore.findById(equipmentId)
                : equipmentStore.findById(equipmentId, branchId);
    }

    private EquipmentDetails registerInBranch(
            UUID id,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return branchContextResolver == null
                ? equipmentStore.register(id, definition, actor, occurredAt)
                : equipmentStore.register(id, definition, actor, occurredAt, branchId);
    }

    private boolean serialExistsInBranch(
            String serialNumber,
            UUID excludeEquipmentId,
            UUID branchId) {
        return branchContextResolver == null
                ? equipmentStore.existsBySerialNumberIgnoreCase(
                        serialNumber, excludeEquipmentId)
                : equipmentStore.existsBySerialNumberIgnoreCase(
                        serialNumber, excludeEquipmentId, branchId);
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(
                branchContextResolver.resolveOperation(actor.id()));
    }
}
