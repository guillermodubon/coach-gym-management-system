package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentOperations;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.application.command.ChangeIncidentPriorityCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ReportIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ResolveIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartIncidentInvestigationCommand;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusTransition;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IncidentApplicationServiceTest {

    private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(ACTOR_ID, "admin");

    @Mock private IncidentStore incidentStore;
    @Mock private EquipmentLookup equipmentLookup;
    @Mock private EquipmentIncidentOperations equipmentIncidentOperations;
    @Mock private ApplicationEventPublisher eventPublisher;

    private IncidentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new IncidentApplicationService(
                incidentStore,
                equipmentLookup,
                equipmentIncidentOperations,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reportsIncidentAndWithdrawsAvailableEquipment() {
        EquipmentDetails equipment = equipment(EquipmentStatus.AVAILABLE, 3L);
        IncidentDetails reported = incident(
                IncidentStatus.OPEN, IncidentPriority.CRITICAL, 0L,
                null, null, null);
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment));
        when(incidentStore.report(any(IncidentDefinition.class), eq(ACTOR), eq(NOW)))
                .thenReturn(reported);

        IncidentDetails result = service.report(
                new ReportIncidentCommand(
                        EQUIPMENT_ID,
                        IncidentPriority.CRITICAL,
                        "Emergency stop fails.",
                        true,
                        3L),
                ACTOR);

        assertThat(result).isSameAs(reported);
        verify(equipmentIncidentOperations).takeOutOfServiceForIncident(
                EQUIPMENT_ID, "INC-000001", 3L, ACTOR, NOW);

        ArgumentCaptor<IncidentReportedEvent> event =
                ArgumentCaptor.forClass(IncidentReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().takenOutOfService()).isTrue();
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void reportWithoutWithdrawalDoesNotInvokeEquipmentMutation() {
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.AVAILABLE, 3L)));
        when(incidentStore.report(any(), eq(ACTOR), eq(NOW)))
                .thenReturn(incident(
                        IncidentStatus.OPEN, IncidentPriority.LOW, 0L,
                        null, null, null));

        service.report(new ReportIncidentCommand(
                EQUIPMENT_ID, IncidentPriority.LOW,
                "Minor noise.", false, null), ACTOR);

        verify(equipmentIncidentOperations, never())
                .takeOutOfServiceForIncident(any(), any(), any(Long.class), any(), any());
    }

    @Test
    void retiredEquipmentIsRejectedBeforeIncidentPersistence() {
        when(equipmentLookup.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(equipment(EquipmentStatus.RETIRED, 4L)));

        assertThatThrownBy(() -> service.report(
                new ReportIncidentCommand(
                        EQUIPMENT_ID, IncidentPriority.HIGH,
                        "Failure.", false, null),
                ACTOR))
                .isInstanceOf(IncidentEquipmentUnavailableException.class)
                .hasMessageContaining("Retired equipment");

        verify(incidentStore, never()).report(any(), any(), any());
    }

    @Test
    void startsInvestigationAndPublishesEvent() {
        IncidentDetails current = incident(
                IncidentStatus.OPEN, IncidentPriority.HIGH, 0L,
                null, null, null);
        IncidentDetails updated = incident(
                IncidentStatus.IN_PROGRESS, IncidentPriority.HIGH, 1L,
                null, null, null);
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.of(current));
        when(incidentStore.transitionStatus(
                eq(INCIDENT_ID), eq(0L), any(IncidentStatusTransition.class),
                eq(null), eq(ACTOR), eq(NOW)))
                .thenReturn(updated);

        IncidentDetails result = service.startInvestigation(
                new StartIncidentInvestigationCommand(
                        INCIDENT_ID, "Investigation started.", 0L),
                ACTOR);

        assertThat(result.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        verify(eventPublisher).publishEvent(
                any(IncidentInvestigationStartedEvent.class));
    }

    @Test
    void changesPriorityAndPublishesPreviousAndNewValues() {
        IncidentDetails current = incident(
                IncidentStatus.OPEN, IncidentPriority.HIGH, 0L,
                null, null, null);
        IncidentDetails updated = incident(
                IncidentStatus.OPEN, IncidentPriority.CRITICAL, 1L,
                null, null, null);
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.of(current));
        when(incidentStore.changePriority(
                INCIDENT_ID, 0L, IncidentPriority.CRITICAL, ACTOR, NOW))
                .thenReturn(updated);

        service.changePriority(
                new ChangeIncidentPriorityCommand(
                        INCIDENT_ID, IncidentPriority.CRITICAL,
                        "Immediate risk.", 0L),
                ACTOR);

        ArgumentCaptor<IncidentPriorityChangedEvent> event =
                ArgumentCaptor.forClass(IncidentPriorityChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousPriority())
                .isEqualTo(IncidentPriority.HIGH);
        assertThat(event.getValue().newPriority())
                .isEqualTo(IncidentPriority.CRITICAL);
    }

    @Test
    void repeatedPriorityIsRejectedWithoutPersistence() {
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.of(
                incident(IncidentStatus.OPEN, IncidentPriority.HIGH, 0L,
                        null, null, null)));

        assertThatThrownBy(() -> service.changePriority(
                new ChangeIncidentPriorityCommand(
                        INCIDENT_ID, IncidentPriority.HIGH,
                        "No change.", 0L),
                ACTOR))
                .isInstanceOf(IncidentStateConflictException.class);

        verify(incidentStore, never()).changePriority(
                any(), any(Long.class), any(), any(), any());
    }

    @Test
    void resolvesInProgressIncident() {
        IncidentDetails current = incident(
                IncidentStatus.IN_PROGRESS, IncidentPriority.HIGH, 1L,
                null, null, null);
        IncidentDetails resolved = incident(
                IncidentStatus.RESOLVED, IncidentPriority.HIGH, 2L,
                NOW, ACTOR_ID, "Controller replaced.");
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.of(current));
        when(incidentStore.transitionStatus(
                eq(INCIDENT_ID), eq(1L), any(IncidentStatusTransition.class),
                eq("Controller replaced."), eq(ACTOR), eq(NOW)))
                .thenReturn(resolved);

        IncidentDetails result = service.resolve(
                new ResolveIncidentCommand(
                        INCIDENT_ID, "Controller replaced.", 1L),
                ACTOR);

        assertThat(result.status()).isEqualTo(IncidentStatus.RESOLVED);
        verify(eventPublisher).publishEvent(any(IncidentResolvedEvent.class));
    }

    @Test
    void openIncidentCannotBeResolvedDirectly() {
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.of(
                incident(IncidentStatus.OPEN, IncidentPriority.HIGH, 0L,
                        null, null, null)));

        assertThatThrownBy(() -> service.resolve(
                new ResolveIncidentCommand(
                        INCIDENT_ID, "Resolved.", 0L),
                ACTOR))
                .isInstanceOf(IncidentStateConflictException.class)
                .hasMessageContaining("OPEN to RESOLVED");

        verify(incidentStore, never()).transitionStatus(
                any(), any(Long.class), any(), any(), any(), any());
    }

    @Test
    void findByIdRejectsUnknownIncident() {
        when(incidentStore.findById(INCIDENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(INCIDENT_ID))
                .isInstanceOf(IncidentNotFoundException.class);
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
                status == EquipmentStatus.RETIRED ? "Not repairable." : null,
                ACTOR_ID, ACTOR_ID, NOW.minusSeconds(3600), NOW, version);
    }

    private static IncidentDetails incident(
            IncidentStatus status,
            IncidentPriority priority,
            long version,
            Instant resolvedAt,
            UUID resolvedBy,
            String resolutionNotes) {
        return new IncidentDetails(
                INCIDENT_ID, 1L, "INC-000001",
                EQUIPMENT_ID, "EQP-000001", "Treadmill",
                status, priority, "Motor failure.",
                NOW.minusSeconds(300), ACTOR_ID, null,
                resolvedAt, resolvedBy, resolutionNotes,
                NOW.minusSeconds(300), NOW, version);
    }
}
