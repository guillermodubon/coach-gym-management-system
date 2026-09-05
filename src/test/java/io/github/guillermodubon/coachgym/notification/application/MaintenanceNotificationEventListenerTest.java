package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationLookup;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceNotificationEventListenerTest {

    @Mock private MaintenanceNotificationLookup maintenanceLookup;
    @Mock private NotificationDeliveryService deliveryService;
    private MaintenanceNotificationEventListener listener;
    private UUID maintenanceId;
    private UUID creatorId;
    private UUID assignedId;

    @BeforeEach
    void setUp() {
        listener = new MaintenanceNotificationEventListener(
                maintenanceLookup, deliveryService);
        maintenanceId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        assignedId = UUID.randomUUID();
        when(maintenanceLookup.findById(maintenanceId)).thenReturn(Optional.of(
                new MaintenanceNotificationDetails(
                        maintenanceId, "MNT-000001", UUID.randomUUID(),
                        "EQP-000001", creatorId, assignedId)));
    }

    @Test
    void scheduledMaintenanceNotifiesAssignedUserExceptActor() {
        UUID actorId = UUID.randomUUID();
        listener.on(new MaintenanceScheduledEvent(
                maintenanceId, "MNT-000001", UUID.randomUUID(), "EQP-000001",
                null, MaintenanceType.PREVENTIVE, LocalDate.of(2026, 9, 10),
                BigDecimal.TEN, "USD", actorId, "admin", now()));

        ArgumentCaptor<NotificationDefinition> captor =
                ArgumentCaptor.forClass(NotificationDefinition.class);
        verify(deliveryService).deliver(captor.capture());
        assertThat(captor.getValue().recipientUserId()).isEqualTo(assignedId);
    }

    @Test
    void completionOutOfServiceCreatesWarningForCreator() {
        listener.on(new MaintenanceCompletedEvent(
                maintenanceId, "MNT-000001", UUID.randomUUID(), "EQP-000001",
                null, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.COMPLETED,
                EquipmentMaintenanceOutcome.OUT_OF_SERVICE, BigDecimal.TEN, "USD",
                assignedId, "assigned-user", now()));

        ArgumentCaptor<NotificationDefinition> captor =
                ArgumentCaptor.forClass(NotificationDefinition.class);
        verify(deliveryService).deliver(captor.capture());
        assertThat(captor.getValue().recipientUserId()).isEqualTo(creatorId);
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void cancellationDeduplicatesCreatorAndAssigneeAndSkipsActor() {
        when(maintenanceLookup.findById(maintenanceId)).thenReturn(Optional.of(
                new MaintenanceNotificationDetails(
                        maintenanceId, "MNT-000001", UUID.randomUUID(),
                        "EQP-000001", creatorId, creatorId)));
        listener.on(new MaintenanceCancelledEvent(
                maintenanceId, "MNT-000001", UUID.randomUUID(), "EQP-000001",
                null, MaintenanceStatus.SCHEDULED, MaintenanceStatus.CANCELLED,
                null, assignedId, "admin", now()));
        verify(deliveryService, times(1)).deliver(any());
    }

    @Test
    void doesNotNotifyActorWhenActorIsOnlyRecipient() {
        listener.on(new MaintenanceScheduledEvent(
                maintenanceId, "MNT-000001", UUID.randomUUID(), "EQP-000001",
                null, MaintenanceType.PREVENTIVE, LocalDate.of(2026, 9, 10),
                null, "USD", assignedId, "assigned-user", now()));
        verify(deliveryService, never()).deliver(any());
    }

    private static Instant now() {
        return Instant.parse("2026-09-05T17:00:00Z");
    }
}
