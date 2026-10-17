package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateSerialNumberException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryInactiveException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EquipmentUpdateServiceTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2025-09-01T08:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    FIXED_NOW,
                    ZoneOffset.UTC);

    private static final UUID EQUIPMENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CATEGORY_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_CATEGORY_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000002");

    private static final UUID ADMIN_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final AuthenticatedActor ADMIN =
            new AuthenticatedActor(
                    ADMIN_ID,
                    "admin@gym.com");

    @Mock
    private EquipmentStore equipmentStore;

    @Mock
    private EquipmentCategoryStore categoryStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EquipmentApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new EquipmentApplicationService(
                        equipmentStore,
                        categoryStore,
                        eventPublisher,
                        CLOCK);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EquipmentCategoryDetails activeCategory(
            UUID categoryId) {

        return new EquipmentCategoryDetails(
                categoryId,
                categoryId.equals(SECOND_CATEGORY_ID)
                        ? "Weights"
                        : "Cardio",
                null,
                true,
                FIXED_NOW,
                FIXED_NOW,
                0L);
    }

    private EquipmentCategoryDetails inactiveCategory(
            UUID categoryId) {

        return new EquipmentCategoryDetails(
                categoryId,
                "Inactive",
                null,
                false,
                FIXED_NOW,
                FIXED_NOW,
                0L);
    }

    private EquipmentDetails existingEquipment(
            long version) {

        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "Treadmill",
                null,
                null,
                null,
                null,
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                ADMIN.id(),
                null,
                FIXED_NOW,
                FIXED_NOW,
                version);
    }

    private EquipmentDetails updatedEquipment() {
        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "New Name",
                "Acme",
                "X200",
                null,
                "Room B",
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                ADMIN.id(),
                ADMIN.id(),
                FIXED_NOW,
                FIXED_NOW,
                1L);
    }

    private EquipmentDetails reassignedEquipment() {
        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                SECOND_CATEGORY_ID,
                "Weights",
                "Barbell",
                null,
                null,
                null,
                null,
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                ADMIN.id(),
                ADMIN.id(),
                FIXED_NOW,
                FIXED_NOW,
                1L);
    }

    private UpdateEquipmentCommand updateCommand(
            long version) {

        return updateCommand(
                CATEGORY_ID,
                "New Name",
                null,
                version);
    }

    private UpdateEquipmentCommand updateCommand(
            UUID categoryId,
            String name,
            String serialNumber,
            long version) {

        EquipmentDefinition definition =
                EquipmentDefinition.create(
                        categoryId,
                        name,
                        null,
                        null,
                        serialNumber,
                        null,
                        null,
                        null);

        return new UpdateEquipmentCommand(
                EQUIPMENT_ID,
                definition,
                version);
    }

    private void prepareSuccessfulUpdate(
            EquipmentDetails updatedDetails) {

        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(
                updatedDetails.categoryId()))
                .thenReturn(
                        Optional.of(
                                activeCategory(
                                        updatedDetails.categoryId())));

        when(equipmentStore.update(
                eq(EQUIPMENT_ID),
                any(EquipmentDefinition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED_NOW)))
                .thenReturn(updatedDetails);
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void update_persistsAndPublishesUpdatedEvent() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(0L),
                        ADMIN);

        assertThat(result.equipmentCode())
                .isEqualTo("EQP-000001");

        assertThat(result.name())
                .isEqualTo("New Name");

        assertThat(result.version())
                .isEqualTo(1L);

        verify(equipmentStore)
                .update(
                        eq(EQUIPMENT_ID),
                        any(EquipmentDefinition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED_NOW));

        ArgumentCaptor<EquipmentUpdatedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentUpdatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentUpdatedEvent event =
                captor.getValue();

        assertThat(event.equipmentId())
                .isEqualTo(EQUIPMENT_ID);

        assertThat(event.equipmentCode())
                .isEqualTo("EQP-000001");

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.occurredAt())
                .isEqualTo(FIXED_NOW);
    }

    @Test
    void update_persistBeforePublish() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        InOrder order =
                inOrder(
                        equipmentStore,
                        eventPublisher);

        service.update(
                updateCommand(0L),
                ADMIN);

        order.verify(equipmentStore)
                .update(
                        eq(EQUIPMENT_ID),
                        any(EquipmentDefinition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED_NOW));

        order.verify(eventPublisher)
                .publishEvent(
                        any(EquipmentUpdatedEvent.class));
    }

    @Test
    void update_equipmentCodeRemainsUnchanged() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(0L),
                        ADMIN);

        assertThat(result.equipmentCode())
                .isEqualTo("EQP-000001");

        assertThat(result.equipmentCode())
                .isEqualTo(
                        existingEquipment(0L)
                                .equipmentCode());
    }

    @Test
    void update_statusRemainsUnchanged() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(0L),
                        ADMIN);

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);

        assertThat(result.status())
                .isEqualTo(
                        existingEquipment(0L)
                                .status());
    }

    @Test
    void update_versionIncrementsAfterSuccess() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(0L),
                        ADMIN);

        assertThat(result.version())
                .isEqualTo(1L);
    }

    @Test
    void update_skipsSerialCheck_whenSerialIsNull() {
        prepareSuccessfulUpdate(
                updatedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(
                                CATEGORY_ID,
                                "New Name",
                                null,
                                0L),
                        ADMIN);

        assertThat(result.name())
                .isEqualTo("New Name");

        verify(equipmentStore, never())
                .existsBySerialNumberIgnoreCase(
                        any(String.class),
                        any(UUID.class));
    }

    @Test
    void update_checksSerialUniquenessExcludingCurrentEquipment() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(CATEGORY_ID))
                .thenReturn(
                        Optional.of(
                                activeCategory(CATEGORY_ID)));

        when(equipmentStore.existsBySerialNumberIgnoreCase(
                eq("SN-001"),
                eq(EQUIPMENT_ID)))
                .thenReturn(false);

        when(equipmentStore.update(
                eq(EQUIPMENT_ID),
                any(EquipmentDefinition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED_NOW)))
                .thenReturn(
                        updatedEquipment());

        service.update(
                updateCommand(
                        CATEGORY_ID,
                        "Bike",
                        "SN-001",
                        0L),
                ADMIN);

        verify(equipmentStore)
                .existsBySerialNumberIgnoreCase(
                        eq("SN-001"),
                        eq(EQUIPMENT_ID));
    }

    // -------------------------------------------------------------------------
    // Category reassignment
    // -------------------------------------------------------------------------

    @Test
    void update_allowsCategoryReassignment_whenNewCategoryActive() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(
                SECOND_CATEGORY_ID))
                .thenReturn(
                        Optional.of(
                                activeCategory(
                                        SECOND_CATEGORY_ID)));

        when(equipmentStore.update(
                eq(EQUIPMENT_ID),
                any(EquipmentDefinition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED_NOW)))
                .thenReturn(
                        reassignedEquipment());

        EquipmentDetails result =
                service.update(
                        updateCommand(
                                SECOND_CATEGORY_ID,
                                "Barbell",
                                null,
                                0L),
                        ADMIN);

        assertThat(result.categoryId())
                .isEqualTo(
                        SECOND_CATEGORY_ID);

        assertThat(result.categoryName())
                .isEqualTo("Weights");

        assertThat(result.equipmentCode())
                .isEqualTo("EQP-000001");

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);
    }

    // -------------------------------------------------------------------------
    // Failure cases
    // -------------------------------------------------------------------------

    @Test
    void update_throwsNotFound_whenEquipmentMissing() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(
                        updateCommand(0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentNotFoundException.class);

        verify(categoryStore, never())
                .findById(any(UUID.class));

        verify(equipmentStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentDefinition.class),
                        any(AuthenticatedActor.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void update_throwsCategoryNotFound_whenCategoryMissing() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(CATEGORY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(
                        updateCommand(0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentCategoryNotFoundException.class);

        verify(equipmentStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentDefinition.class),
                        any(AuthenticatedActor.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void update_throwsCategoryInactive_whenCategoryNotActive() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(CATEGORY_ID))
                .thenReturn(
                        Optional.of(
                                inactiveCategory(CATEGORY_ID)));

        assertThatThrownBy(() ->
                service.update(
                        updateCommand(0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentCategoryInactiveException.class);

        verify(equipmentStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentDefinition.class),
                        any(AuthenticatedActor.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void update_throwsDuplicateSerial_whenSerialTakenByOther() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(CATEGORY_ID))
                .thenReturn(
                        Optional.of(
                                activeCategory(CATEGORY_ID)));

        when(equipmentStore.existsBySerialNumberIgnoreCase(
                eq("SN-TAKEN"),
                eq(EQUIPMENT_ID)))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.update(
                        updateCommand(
                                CATEGORY_ID,
                                "Bike",
                                "SN-TAKEN",
                                0L),
                        ADMIN))
                .isInstanceOf(
                        DuplicateSerialNumberException.class);

        verify(equipmentStore)
                .existsBySerialNumberIgnoreCase(
                        eq("SN-TAKEN"),
                        eq(EQUIPMENT_ID));

        verify(equipmentStore, never())
                .update(
                        any(UUID.class),
                        any(EquipmentDefinition.class),
                        any(AuthenticatedActor.class),
                        anyLong(),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void update_noEventOnPersistenceFailure() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                existingEquipment(0L)));

        when(categoryStore.findById(CATEGORY_ID))
                .thenReturn(
                        Optional.of(
                                activeCategory(CATEGORY_ID)));

        when(equipmentStore.update(
                eq(EQUIPMENT_ID),
                any(EquipmentDefinition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED_NOW)))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        assertThatThrownBy(() ->
                service.update(
                        updateCommand(0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentVersionConflictException.class);

        verify(equipmentStore)
                .update(
                        eq(EQUIPMENT_ID),
                        any(EquipmentDefinition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED_NOW));

        verify(eventPublisher, never())
                .publishEvent(any());
    }
}
