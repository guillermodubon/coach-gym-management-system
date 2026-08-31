package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.application.command.RegisterEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateSerialNumberException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryInactiveException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentApplicationServiceTest {

    @Mock private EquipmentStore equipmentStore;
    @Mock private EquipmentCategoryStore categoryStore;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Instant FIXED_NOW = Instant.parse("2025-06-01T09:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final AuthenticatedActor admin = new AuthenticatedActor(UUID.randomUUID(), "admin@gym.com");

    private EquipmentApplicationService service;

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EquipmentApplicationService(equipmentStore, categoryStore, eventPublisher, clock);
    }

    private EquipmentCategoryDetails activeCategory() {
        return new EquipmentCategoryDetails(CATEGORY_ID, "Cardio", null, true,
                FIXED_NOW, FIXED_NOW, 0L);
    }

    private EquipmentCategoryDetails inactiveCategory() {
        return new EquipmentCategoryDetails(CATEGORY_ID, "Cardio", null, false,
                FIXED_NOW, FIXED_NOW, 0L);
    }

    private EquipmentDetails sampleEquipment(UUID id) {
        return new EquipmentDetails(id, 1L, "EQP-000001", CATEGORY_ID, "Cardio",
                "Treadmill", null, null, null, null,
                EquipmentStatus.AVAILABLE, null, null, null, null, null,
                admin.id(), null, FIXED_NOW, FIXED_NOW, 0L);
    }

    private RegisterEquipmentCommand simpleRegisterCommand() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Treadmill", null, null, null, null, null, null);
        return new RegisterEquipmentCommand(def);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_persistsAndPublishesEvent_whenCategoryActiveAndNoSerial() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory()));
        when(equipmentStore.register(any(), any(), eq(admin), eq(FIXED_NOW)))
                .thenReturn(sampleEquipment(EQUIPMENT_ID));

        EquipmentDetails result = service.register(simpleRegisterCommand(), admin);

        assertThat(result.equipmentCode()).isEqualTo("EQP-000001");
        assertThat(result.status()).isEqualTo(EquipmentStatus.AVAILABLE);

        ArgumentCaptor<EquipmentRegisteredEvent> captor =
                ArgumentCaptor.forClass(EquipmentRegisteredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EquipmentRegisteredEvent event = captor.getValue();
        assertThat(event.equipmentId()).isEqualTo(EQUIPMENT_ID);
        assertThat(event.equipmentCode()).isEqualTo("EQP-000001");
        assertThat(event.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(event.actorUserId()).isEqualTo(admin.id());
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void register_throwsCategoryNotFound_whenCategoryMissing() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(simpleRegisterCommand(), admin))
                .isInstanceOf(EquipmentCategoryNotFoundException.class);

        verify(equipmentStore, never()).register(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void register_throwsCategoryInactive_whenCategoryNotActive() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(inactiveCategory()));

        assertThatThrownBy(() -> service.register(simpleRegisterCommand(), admin))
                .isInstanceOf(EquipmentCategoryInactiveException.class);

        verify(equipmentStore, never()).register(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void register_throwsDuplicateSerial_whenSerialExists() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory()));
        EquipmentDefinition defWithSerial = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, "SN-001", null, null, null);
        when(equipmentStore.existsBySerialNumberIgnoreCase(eq("SN-001"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterEquipmentCommand(defWithSerial), admin))
                .isInstanceOf(DuplicateSerialNumberException.class);

        verify(equipmentStore, never()).register(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void register_doesNotCheckSerial_whenSerialIsNull() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory()));
        when(equipmentStore.register(any(), any(), eq(admin), eq(FIXED_NOW)))
                .thenReturn(sampleEquipment(EQUIPMENT_ID));

        service.register(simpleRegisterCommand(), admin);

        verify(equipmentStore, never()).existsBySerialNumberIgnoreCase(any(), any());
    }

    @Test
    void register_usesFixedClockTimestamp() {
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory()));
        when(equipmentStore.register(any(), any(), eq(admin), eq(FIXED_NOW)))
                .thenReturn(sampleEquipment(EQUIPMENT_ID));

        service.register(simpleRegisterCommand(), admin);

        ArgumentCaptor<Instant> occurredAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(equipmentStore).register(any(), any(), any(), occurredAtCaptor.capture());
        assertThat(occurredAtCaptor.getValue()).isEqualTo(FIXED_NOW);
    }

    @Test
    void register_eventPublishedAfterPersistence() {
        // Verify ordering by confirming store.register completes before eventPublisher.publishEvent.
        when(categoryStore.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory()));
        when(equipmentStore.register(any(), any(), eq(admin), eq(FIXED_NOW)))
                .thenReturn(sampleEquipment(EQUIPMENT_ID));

        var inOrder = org.mockito.Mockito.inOrder(equipmentStore, eventPublisher);
        service.register(simpleRegisterCommand(), admin);
        inOrder.verify(equipmentStore).register(any(), any(), any(), any());
        inOrder.verify(eventPublisher).publishEvent(any(EquipmentRegisteredEvent.class));
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsDetails_whenExists() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(sampleEquipment(EQUIPMENT_ID)));
        EquipmentDetails result = service.findById(EQUIPMENT_ID);
        assertThat(result.id()).isEqualTo(EQUIPMENT_ID);
        assertThat(result.equipmentCode()).isEqualTo("EQP-000001");
    }

    @Test
    void findById_throwsNotFound_whenMissing() {
        when(equipmentStore.findById(EQUIPMENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(EQUIPMENT_ID))
                .isInstanceOf(EquipmentNotFoundException.class);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_delegatesToStore() {
        EquipmentSearchQuery query = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, null);
        EquipmentPage expected = new EquipmentPage(List.of(), 0, 25, 0L, 0);
        when(equipmentStore.findAll(query)).thenReturn(expected);
        assertThat(service.findAll(query)).isEqualTo(expected);
    }
}
