package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceNotFoundException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceStateConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
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
class EquipmentMaintenanceApplicationServiceTest {

    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-04T01:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "admin");

    @Mock private EquipmentStore equipmentStore;
    @Mock private ApplicationEventPublisher eventPublisher;
    private EquipmentMaintenanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentMaintenanceApplicationService(
                equipmentStore, eventPublisher);
    }

    @Test
    void startsMaintenanceFromAvailableEquipment() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.AVAILABLE, 2L)));
        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID), any(EquipmentStatusTransition.class),
                eq(ACTOR), eq(2L), eq(NOW)))
                .thenReturn(equipment(EquipmentStatus.MAINTENANCE, 3L));

        EquipmentDetails result = service.startMaintenance(
                EQUIPMENT_ID, "MNT-000001", 2L, ACTOR, NOW);

        assertThat(result.status()).isEqualTo(EquipmentStatus.MAINTENANCE);
        ArgumentCaptor<EquipmentStatusTransition> transition =
                ArgumentCaptor.forClass(EquipmentStatusTransition.class);
        verify(equipmentStore).applyTransition(
                eq(EQUIPMENT_ID), transition.capture(), eq(ACTOR), eq(2L), eq(NOW));
        assertThat(transition.getValue().reason()).contains("MNT-000001");
        verify(eventPublisher).publishEvent(any(EquipmentStatusChangedEvent.class));
    }

    @Test
    void completesMaintenanceToApprovedOutcome() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.MAINTENANCE, 3L)));
        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID), any(), eq(ACTOR), eq(3L), eq(NOW)))
                .thenReturn(equipment(EquipmentStatus.AVAILABLE, 4L));

        EquipmentDetails result = service.completeMaintenance(
                EQUIPMENT_ID, "MNT-000001", EquipmentStatus.AVAILABLE,
                3L, ACTOR, NOW);

        assertThat(result.status()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void rejectsRetiredAndAlreadyMaintainedEquipment() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.RETIRED, 2L)));
        assertThatThrownBy(() -> service.startMaintenance(
                EQUIPMENT_ID, "MNT-1", 2L, ACTOR, NOW))
                .isInstanceOf(EquipmentMaintenanceStateConflictException.class);

        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.MAINTENANCE, 2L)));
        assertThatThrownBy(() -> service.startMaintenance(
                EQUIPMENT_ID, "MNT-1", 2L, ACTOR, NOW))
                .isInstanceOf(EquipmentMaintenanceStateConflictException.class);
        verify(equipmentStore, never()).applyTransition(any(), any(), any(), any(Long.class), any());
    }

    @Test
    void translatesNotFoundAndVersionConflictsToPublicExceptions() {
        when(equipmentStore.findById(EQUIPMENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startMaintenance(
                EQUIPMENT_ID, "MNT-1", 0L, ACTOR, NOW))
                .isInstanceOf(EquipmentMaintenanceNotFoundException.class);

        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.AVAILABLE, 2L)));
        when(equipmentStore.applyTransition(any(), any(), any(), any(Long.class), any()))
                .thenThrow(new EquipmentVersionConflictException(EQUIPMENT_ID));
        assertThatThrownBy(() -> service.startMaintenance(
                EQUIPMENT_ID, "MNT-1", 2L, ACTOR, NOW))
                .isInstanceOf(EquipmentMaintenanceVersionConflictException.class);
    }

    @Test
    void rejectsRetirementAsMaintenanceOutcome() {
        assertThatThrownBy(() -> service.completeMaintenance(
                EQUIPMENT_ID, "MNT-1", EquipmentStatus.RETIRED,
                1L, ACTOR, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AVAILABLE or OUT_OF_SERVICE");
    }

    private static EquipmentDetails equipment(EquipmentStatus status, long version) {
        return new EquipmentDetails(
                EQUIPMENT_ID, 1L, "EQP-000001", UUID.randomUUID(),
                "Cardio", "Treadmill", null, null, null, "Floor 1",
                status, null, null,
                status == EquipmentStatus.RETIRED ? NOW : null,
                status == EquipmentStatus.RETIRED ? ACTOR_ID : null,
                status == EquipmentStatus.RETIRED ? "End of life." : null,
                ACTOR_ID, ACTOR_ID, NOW.minusSeconds(3600), NOW, version);
    }
}
