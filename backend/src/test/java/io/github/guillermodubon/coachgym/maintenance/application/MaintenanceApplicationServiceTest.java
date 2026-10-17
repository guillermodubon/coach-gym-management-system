package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceOperations;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentLookup;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.command.CancelMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.CompleteMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ScheduleMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartMaintenanceCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MaintenanceApplicationServiceTest {

    private static final UUID MAINTENANCE_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-04T04:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "admin");

    @Mock private MaintenanceStore maintenanceStore;
    @Mock private EquipmentLookup equipmentLookup;
    @Mock private IncidentLookup incidentLookup;
    @Mock private EquipmentMaintenanceOperations equipmentOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    private MaintenanceApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new MaintenanceApplicationService(
                        maintenanceStore,
                        equipmentLookup,
                        incidentLookup,
                        equipmentOperations,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC),
                        eventPublisher);
    }

    @Test
    void schedulesPreventiveMaintenanceForNonRetiredEquipment() {
        var command = scheduleCommand(null, MaintenanceType.PREVENTIVE);
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.AVAILABLE, 2L)));
        when(maintenanceStore.schedule(any(), eq(ACTOR), eq(NOW)))
                .thenReturn(maintenance(MaintenanceStatus.SCHEDULED, null, 0L));

        MaintenanceDetails result = service.schedule(command, ACTOR);

        assertThat(result.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        verify(maintenanceStore).schedule(any(), eq(ACTOR), eq(NOW));
    }

    @Test
    void validatesCorrectiveIncidentOwnershipAndState() {
        var command = scheduleCommand(INCIDENT_ID, MaintenanceType.CORRECTIVE);
        IncidentDetails incident = org.mockito.Mockito.mock(IncidentDetails.class);
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.AVAILABLE, 2L)));
        when(incidentLookup.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));
        when(incident.equipmentId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.schedule(command, ACTOR))
                .isInstanceOf(MaintenanceIncidentMismatchException.class);
        verify(maintenanceStore, never()).schedule(any(), any(), any());

        when(incident.equipmentId()).thenReturn(EQUIPMENT_ID);
        when(incident.status()).thenReturn(IncidentStatus.RESOLVED);
        assertThatThrownBy(() -> service.schedule(command, ACTOR))
                .isInstanceOf(MaintenanceStateConflictException.class)
                .hasMessageContaining("Resolved incidents");
    }

    @Test
    void rejectsRetiredEquipmentAtScheduling() {
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.RETIRED, 2L)));

        assertThatThrownBy(() -> service.schedule(
                scheduleCommand(null, MaintenanceType.PREVENTIVE), ACTOR))
                .isInstanceOf(MaintenanceEquipmentUnavailableException.class);
    }

    @Test
    void startsMaintenanceAndCoordinatesEquipment() {
        MaintenanceDetails current = maintenance(
                MaintenanceStatus.SCHEDULED, null, 0L);
        when(maintenanceStore.findById(MAINTENANCE_ID))
                .thenReturn(Optional.of(current));
        when(maintenanceStore.existsByEquipmentIdAndStatus(
                EQUIPMENT_ID, MaintenanceStatus.IN_PROGRESS)).thenReturn(false);
        when(maintenanceStore.transitionStatus(
                eq(MAINTENANCE_ID), eq(0L), any(), eq(NOW), eq(ACTOR), eq(NOW)))
                .thenReturn(maintenance(MaintenanceStatus.IN_PROGRESS, NOW, 1L));

        MaintenanceDetails result = service.start(
                new StartMaintenanceCommand(
                        MAINTENANCE_ID, NOW, "Work started.", 0L, 2L), ACTOR);

        assertThat(result.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        verify(equipmentOperations).startMaintenance(
                EQUIPMENT_ID, "MNT-000001", 2L, ACTOR, NOW);
    }

    @Test
    void rejectsSecondInProgressOrderForEquipment() {
        when(maintenanceStore.findById(MAINTENANCE_ID))
                .thenReturn(Optional.of(maintenance(
                        MaintenanceStatus.SCHEDULED, null, 0L)));
        when(maintenanceStore.existsByEquipmentIdAndStatus(
                EQUIPMENT_ID, MaintenanceStatus.IN_PROGRESS)).thenReturn(true);

        assertThatThrownBy(() -> service.start(
                new StartMaintenanceCommand(
                        MAINTENANCE_ID, NOW, "Start.", 0L, 2L), ACTOR))
                .isInstanceOf(MaintenanceActiveOrderConflictException.class);
        verify(equipmentOperations, never()).startMaintenance(
                any(), any(), any(Long.class), any(), any());
    }

    @Test
    void completesMaintenanceAndReleasesEquipment() {
        Instant startedAt = NOW.minusSeconds(3600);
        when(maintenanceStore.findById(MAINTENANCE_ID))
                .thenReturn(Optional.of(maintenance(
                        MaintenanceStatus.IN_PROGRESS, startedAt, 1L)));
        when(maintenanceStore.complete(
                eq(MAINTENANCE_ID), eq(1L), any(), any(), eq(ACTOR), eq(NOW)))
                .thenReturn(maintenance(MaintenanceStatus.COMPLETED, startedAt, 2L));

        MaintenanceDetails result = service.complete(
                new CompleteMaintenanceCommand(
                        MAINTENANCE_ID, NOW, "Replaced belt.",
                        new BigDecimal("25.00"), "USD",
                        EquipmentMaintenanceOutcome.AVAILABLE, 1L, 3L), ACTOR);

        assertThat(result.status()).isEqualTo(MaintenanceStatus.COMPLETED);
        verify(equipmentOperations).completeMaintenance(
                EQUIPMENT_ID, "MNT-000001", EquipmentStatus.AVAILABLE,
                3L, ACTOR, NOW);
    }

    @Test
    void scheduledCancellationDoesNotModifyEquipment() {
        when(maintenanceStore.findById(MAINTENANCE_ID))
                .thenReturn(Optional.of(maintenance(
                        MaintenanceStatus.SCHEDULED, null, 0L)));
        when(maintenanceStore.cancel(
                eq(MAINTENANCE_ID), eq(0L), eq(MaintenanceStatus.SCHEDULED),
                any(), any(), eq(ACTOR), eq(NOW)))
                .thenReturn(maintenance(MaintenanceStatus.CANCELLED, null, 1L));

        service.cancel(new CancelMaintenanceCommand(
                MAINTENANCE_ID, "Provider unavailable.", null, 0L, null), ACTOR);

        verify(equipmentOperations, never()).cancelInProgressMaintenance(
                any(), any(), any(), any(Long.class), any(), any());
    }

    @Test
    void inProgressCancellationUpdatesEquipmentOutcome() {
        Instant startedAt = NOW.minusSeconds(60);
        when(maintenanceStore.findById(MAINTENANCE_ID))
                .thenReturn(Optional.of(maintenance(
                        MaintenanceStatus.IN_PROGRESS, startedAt, 1L)));
        when(maintenanceStore.cancel(
                eq(MAINTENANCE_ID), eq(1L), eq(MaintenanceStatus.IN_PROGRESS),
                any(), any(), eq(ACTOR), eq(NOW)))
                .thenReturn(maintenance(MaintenanceStatus.CANCELLED, startedAt, 2L));

        service.cancel(new CancelMaintenanceCommand(
                MAINTENANCE_ID, "Unsafe to continue.",
                EquipmentMaintenanceOutcome.OUT_OF_SERVICE, 1L, 3L), ACTOR);

        verify(equipmentOperations).cancelInProgressMaintenance(
                EQUIPMENT_ID, "MNT-000001", EquipmentStatus.OUT_OF_SERVICE,
                3L, ACTOR, NOW);
    }

    private static ScheduleMaintenanceCommand scheduleCommand(
            UUID incidentId,
            MaintenanceType type) {
        return new ScheduleMaintenanceCommand(
                EQUIPMENT_ID, incidentId, type, LocalDate.of(2026, 9, 10),
                null, null, null, "USD", null, null);
    }

    private static MaintenanceDetails maintenance(
            MaintenanceStatus status,
            Instant startedAt,
            long version) {
        return new MaintenanceDetails(
                MAINTENANCE_ID, 1L, "MNT-000001", EQUIPMENT_ID,
                "EQP-000001", "Treadmill", null, null,
                MaintenanceType.PREVENTIVE, status,
                LocalDate.of(2026, 9, 10), startedAt,
                status == MaintenanceStatus.COMPLETED ? NOW : null,
                null, null, null,
                status == MaintenanceStatus.COMPLETED
                        ? new BigDecimal("25.00") : null,
                "USD",
                status == MaintenanceStatus.COMPLETED ? "Replaced belt." : null,
                null, ACTOR_ID, null,
                status == MaintenanceStatus.COMPLETED ? ACTOR_ID : null,
                NOW.minusSeconds(7200), NOW, version);
    }

    private static EquipmentDetails equipment(
            EquipmentStatus status,
            long version) {
        return new EquipmentDetails(
                EQUIPMENT_ID, 1L, "EQP-000001", UUID.randomUUID(),
                "Cardio", "Treadmill", null, null, null, "Floor 1",
                status, null, null,
                status == EquipmentStatus.RETIRED ? NOW : null,
                status == EquipmentStatus.RETIRED ? ACTOR_ID : null,
                status == EquipmentStatus.RETIRED ? "End of life." : null,
                ACTOR_ID, ACTOR_ID, NOW.minusSeconds(7200), NOW, version);
    }
}
