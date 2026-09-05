package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentNotificationEventListenerTest {

    @Mock private NotificationRecipientDirectory recipientDirectory;
    @Mock private NotificationDeliveryService deliveryService;
    private IncidentNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IncidentNotificationEventListener(
                recipientDirectory, deliveryService);
    }

    @Test
    void sendsCriticalIncidentAlertToOtherActiveAdministrators() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(recipientDirectory.findActiveByRole("ADMIN")).thenReturn(List.of(
                new NotificationRecipient(actorId, "actor-admin", Set.of("ADMIN")),
                new NotificationRecipient(recipientId, "other-admin", Set.of("ADMIN"))));

        listener.on(event(actorId, IncidentPriority.CRITICAL));

        ArgumentCaptor<NotificationDefinition> captor =
                ArgumentCaptor.forClass(NotificationDefinition.class);
        verify(deliveryService).deliver(captor.capture());
        NotificationDefinition definition = captor.getValue();
        assertThat(definition.recipientUserId()).isEqualTo(recipientId);
        assertThat(definition.notificationType())
                .isEqualTo(NotificationType.INCIDENT_ASSIGNED);
        assertThat(definition.severity()).isEqualTo(NotificationSeverity.CRITICAL);
        assertThat(definition.content().body()).doesNotContain("password", "token");
    }

    @Test
    void ignoresLowAndMediumPriorityIncidents() {
        listener.on(event(UUID.randomUUID(), IncidentPriority.LOW));
        listener.on(event(UUID.randomUUID(), IncidentPriority.MEDIUM));
        verify(recipientDirectory, never()).findActiveByRole(any());
        verify(deliveryService, never()).deliver(any());
    }

    private static IncidentReportedEvent event(UUID actorId, IncidentPriority priority) {
        return new IncidentReportedEvent(
                UUID.randomUUID(),
                "INC-000001",
                UUID.randomUUID(),
                "EQP-000001",
                priority,
                true,
                actorId,
                "admin-user",
                Instant.parse("2026-09-05T16:00:00Z"));
    }
}
