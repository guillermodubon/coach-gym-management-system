package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EquipmentCategoryApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse(
                    "2025-01-15T10:00:00Z");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Clock CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC);

    private static final AuthenticatedActor ADMIN =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "admin@test.com");

    @Mock
    private EquipmentCategoryStore categoryStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EquipmentCategoryApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new EquipmentCategoryApplicationService(
                        categoryStore,
                        eventPublisher,
                        CLOCK);
    }

    private EquipmentCategoryDetails sampleDetails(
            UUID id,
            String name,
            boolean active,
            long version) {

        return new EquipmentCategoryDetails(
                id,
                name,
                null,
                active,
                NOW,
                NOW,
                version);
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Test
    void createPersistsAndPublishesCreatedEvent() {
        UUID categoryId =
                UUID.randomUUID();

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        "Cardio",
                        null);

        when(categoryStore.existsByNameIgnoreCase(
                eq("Cardio"),
                isNull()))
                .thenReturn(false);

        when(categoryStore.create(
                any(UUID.class),
                eq(definition),
                eq(NOW)))
                .thenReturn(
                        sampleDetails(
                                categoryId,
                                "Cardio",
                                true,
                                0L));

        EquipmentCategoryDetails result =
                service.create(
                        new CreateEquipmentCategoryCommand(
                                definition),
                        ADMIN);

        assertThat(result.name())
                .isEqualTo("Cardio");

        assertThat(result.active())
                .isTrue();

        verify(categoryStore).create(
                any(UUID.class),
                eq(definition),
                eq(NOW));

        ArgumentCaptor<EquipmentCategoryCreatedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentCategoryCreatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentCategoryCreatedEvent event =
                captor.getValue();

        assertThat(event.categoryId())
                .isEqualTo(categoryId);

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void createThrowsDuplicateWhenNameExists() {
        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        "Cardio",
                        null);

        when(categoryStore.existsByNameIgnoreCase(
                eq("Cardio"),
                isNull()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.create(
                        new CreateEquipmentCategoryCommand(
                                definition),
                        ADMIN))
                .isInstanceOf(
                        DuplicateEquipmentCategoryException.class);

        verify(categoryStore, never())
                .create(
                        any(UUID.class),
                        any(EquipmentCategoryDefinition.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Find by ID
    // -------------------------------------------------------------------------

    @Test
    void findByIdReturnsDetailsWhenExists() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(
                        Optional.of(
                                sampleDetails(
                                        categoryId,
                                        "Weights",
                                        true,
                                        1L)));

        EquipmentCategoryDetails result =
                service.findById(categoryId);

        assertThat(result.name())
                .isEqualTo("Weights");

        assertThat(result.id())
                .isEqualTo(categoryId);

        verify(categoryStore)
                .findById(categoryId);
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.findById(categoryId))
                .isInstanceOf(
                        EquipmentCategoryNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Find all
    // -------------------------------------------------------------------------

    @Test
    void findAllDelegatesToStore() {
        EquipmentCategorySearchQuery query =
                EquipmentCategorySearchQuery.from(
                        null,
                        0,
                        25,
                        null,
                        null);

        EquipmentCategoryPage expected =
                new EquipmentCategoryPage(
                        List.of(),
                        0,
                        25,
                        0L,
                        0);

        when(categoryStore.findAll(query))
                .thenReturn(expected);

        EquipmentCategoryPage result =
                service.findAll(query);

        assertThat(result)
                .isEqualTo(expected);

        verify(categoryStore)
                .findAll(query);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Test
    void updatePersistsAndPublishesUpdatedEvent() {
        UUID categoryId =
                UUID.randomUUID();

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(                        "Weights",
                        "Heavy equipment");

        when(categoryStore.findById(categoryId))
                .thenReturn(
                        Optional.of(
                                sampleDetails(
                                        categoryId,
                                        "Old category",
                                        true,
                                        2L)));

        when(categoryStore.existsByNameIgnoreCase(
                eq("Weights"),
                eq(categoryId)))
                .thenReturn(false);

        when(categoryStore.update(
                eq(categoryId),
                eq(definition),
                eq(2L),
                eq(NOW)))
                .thenReturn(
                        sampleDetails(
                                categoryId,
                                "Weights",
                                true,
                                3L));

        EquipmentCategoryDetails result =
                service.update(
                        new UpdateEquipmentCategoryCommand(
                                categoryId,
                                definition,
                                2L),
                        ADMIN);

        assertThat(result.name())
                .isEqualTo("Weights");

        assertThat(result.version())
                .isEqualTo(3L);

        verify(categoryStore).update(
                eq(categoryId),
                eq(definition),
                eq(2L),
                eq(NOW));

        ArgumentCaptor<EquipmentCategoryUpdatedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentCategoryUpdatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentCategoryUpdatedEvent event =
                captor.getValue();

        assertThat(event.categoryId())
                .isEqualTo(categoryId);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void updateThrowsNotFoundWhenCategoryMissing() {
        UUID categoryId =
                UUID.randomUUID();

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        "Strength",
                        null);

        when(categoryStore.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(
                        new UpdateEquipmentCategoryCommand(
                                categoryId,
                                definition,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentCategoryNotFoundException.class);

        verify(categoryStore, never())
                .existsByNameIgnoreCase(
                        any(),
                        any());

        verify(categoryStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentCategoryDefinition.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void updateThrowsDuplicateWhenNameTakenByOtherCategory() {
        UUID categoryId =
                UUID.randomUUID();

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        "Taken",
                        null);

        when(categoryStore.findById(categoryId))
                .thenReturn(
                        Optional.of(
                                sampleDetails(
                                        categoryId,
                                        "Old category",
                                        true,
                                        0L)));

        when(categoryStore.existsByNameIgnoreCase(
                eq("Taken"),
                eq(categoryId)))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.update(
                        new UpdateEquipmentCategoryCommand(
                                categoryId,
                                definition,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        DuplicateEquipmentCategoryException                        .class);

        verify(categoryStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentCategoryDefinition.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Activate
    // -------------------------------------------------------------------------

    @Test
    void activatePersistsAndPublishesActivatedEvent() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(
                        Optional.of(
                                sampleDetails(
                                        categoryId,
                                        "Cardio",
                                        false,
                                        1L)));

        when(categoryStore.activate(
                eq(categoryId),
                eq(1L),
                eq(NOW)))
                .thenReturn(
                        sampleDetails(
                                categoryId,
                                "Cardio",
                                true,
                                2L));

        EquipmentCategoryDetails result =
                service.activate(
                        new ActivateEquipmentCategoryCommand(
                                categoryId,
                                1L),
                        ADMIN);

        assertThat(result.active())
                .isTrue();

        assertThat(result.version())
                .isEqualTo(2L);

        verify(categoryStore)
                .activate(
                        eq(categoryId),
                        eq(1L),
                        eq(NOW));

        ArgumentCaptor<EquipmentCategoryActivatedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentCategoryActivatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentCategoryActivatedEvent event =
                captor.getValue();

        assertThat(event.categoryId())
                .isEqualTo(categoryId);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void activateThrowsNotFoundWhenMissing() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.activate(
                        new ActivateEquipmentCategoryCommand(
                                categoryId,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentCategoryNotFoundException.class);

        verify(categoryStore, never())
                .activate(
                        any(UUID.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Deactivate
    // -------------------------------------------------------------------------

    @Test
    void deactivatePersistsAndPublishesDeactivatedEvent() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(
                        Optional.of(
                                sampleDetails(
                                        categoryId,
                                        "Cardio",
                                        true,
                                        1L)));

        when(categoryStore.deactivate(
                eq(categoryId),
                eq(1L),
                eq(NOW)))
                .thenReturn(
                        sampleDetails(
                                categoryId,
                                "Cardio",
                                false,
                                2L));

        EquipmentCategoryDetails result =
                service.deactivate(
                        new DeactivateEquipmentCategoryCommand(
                                categoryId,                                1L),
                        ADMIN);

        assertThat(result.active())
                .isFalse();

        assertThat(result.version())
                .isEqualTo(2L);

        verify(categoryStore)
                .deactivate(
                        eq(categoryId),
                        eq(1L),
                        eq(NOW));

        ArgumentCaptor<EquipmentCategoryDeactivatedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentCategoryDeactivatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentCategoryDeactivatedEvent event =
                captor.getValue();

        assertThat(event.categoryId())
                .isEqualTo(categoryId);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void deactivateThrowsNotFoundWhenMissing() {
        UUID categoryId =
                UUID.randomUUID();

        when(categoryStore.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.deactivate(
                        new DeactivateEquipmentCategoryCommand(
                                categoryId,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentCategoryNotFoundException.class);

        verify(categoryStore, never())
                .deactivate(
                        any(UUID.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }
}
