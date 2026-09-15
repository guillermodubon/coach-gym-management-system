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
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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

    public EquipmentApplicationService(
            EquipmentStore equipmentStore,
            EquipmentCategoryStore categoryStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.equipmentStore = equipmentStore;
        this.categoryStore = categoryStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        if (definition.serialNumber() != null &&
                equipmentStore.existsBySerialNumberIgnoreCase(definition.serialNumber(), null)) {
            throw new DuplicateSerialNumberException(definition.serialNumber());
        }

        UUID id = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        EquipmentDetails registered = equipmentStore.register(id, definition, actor, occurredAt);

        eventPublisher.publishEvent(new EquipmentRegisteredEvent(
                registered.id(),
                registered.equipmentCode(),
                registered.categoryId(),
                actor.id(),
                actor.username(),
                occurredAt));

        return registered;
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetails update(UpdateEquipmentCommand command, AuthenticatedActor actor) {
        UUID equipmentId = command.equipmentId();
        EquipmentDefinition definition = command.definition();

        // Equipment must exist (provides early 404 before version check).
        equipmentStore.findById(equipmentId)
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
                equipmentStore.existsBySerialNumberIgnoreCase(
                        definition.serialNumber(), equipmentId)) {
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
                occurredAt));

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

        EquipmentDetails current = equipmentStore.findById(equipmentId)
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
                occurredAt));

        return updated;
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentDetails findById(UUID equipmentId) {
        return equipmentStore.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentPage findAll(EquipmentSearchQuery query) {
        return equipmentStore.findAll(query);
    }
}
