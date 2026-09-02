package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.application.command.ActivateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.CreateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.DeactivateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateEquipmentCategoryException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentCategoryApplicationService {

    private final EquipmentCategoryStore categoryStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public EquipmentCategoryApplicationService(
            EquipmentCategoryStore categoryStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.categoryStore = categoryStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentCategoryDetails create(
            CreateEquipmentCategoryCommand command,
            AuthenticatedActor actor) {

        EquipmentCategoryDefinition definition =
                command.definition();

        if (categoryStore.existsByNameIgnoreCase(
                definition.name(),
                null)) {

            throw new DuplicateEquipmentCategoryException(
                    definition.name());
        }

        UUID categoryId =
                UUID.randomUUID();

        Instant occurredAt =
                clock.instant();

        EquipmentCategoryDetails created =
                categoryStore.create(
                        categoryId,
                        definition,
                        occurredAt);

        eventPublisher.publishEvent(
                new EquipmentCategoryCreatedEvent(
                        created.id(),
                        created.name(),
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return created;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentCategoryDetails findById(
            UUID categoryId) {

        return categoryStore
                .findById(categoryId)
                .orElseThrow(() ->
                        new EquipmentCategoryNotFoundException(
                                categoryId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentCategoryPage findAll(
            EquipmentCategorySearchQuery query) {

        return categoryStore.findAll(query);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentCategoryDetails update(
            UpdateEquipmentCategoryCommand command,
            AuthenticatedActor actor) {

        UUID categoryId =
                command.categoryId();

        EquipmentCategoryDefinition definition =
                command.definition();

        requireCategory(categoryId);

        if (categoryStore.existsByNameIgnoreCase(
                definition.name(),
                categoryId)) {

            throw new DuplicateEquipmentCategoryException(
                    definition.name());
        }

        Instant occurredAt =
                clock.instant();

        EquipmentCategoryDetails updated =
                categoryStore.update(
                        categoryId,
                        definition,
                        command.version(),
                        occurredAt);

        eventPublisher.publishEvent(
                new EquipmentCategoryUpdatedEvent(
                        updated.id(),
                        updated.name(),
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentCategoryDetails activate(
            ActivateEquipmentCategoryCommand command,
            AuthenticatedActor actor) {

        UUID categoryId =
                command.categoryId();

        requireCategory(categoryId);

        Instant occurredAt =
                clock.instant();

        EquipmentCategoryDetails updated =
                categoryStore.activate(
                        categoryId,
                        command.version(),
                        occurredAt);

        eventPublisher.publishEvent(
                new EquipmentCategoryActivatedEvent(
                        updated.id(),
                        updated.name(),
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentCategoryDetails deactivate(
            DeactivateEquipmentCategoryCommand command,
            AuthenticatedActor actor) {

        UUID categoryId =
                command.categoryId();

        requireCategory(categoryId);

        Instant occurredAt =
                clock.instant();

        EquipmentCategoryDetails updated =
                categoryStore.deactivate(
                        categoryId,
                        command.version(),
                        occurredAt);

        eventPublisher.publishEvent(
                new EquipmentCategoryDeactivatedEvent(
                        updated.id(),
                        updated.name(),
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return updated;
    }

    private void requireCategory(
            UUID categoryId) {

        categoryStore
                .findById(categoryId)
                .orElseThrow(() ->
                        new EquipmentCategoryNotFoundException(
                                categoryId));
    }
}