package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T15:00:00Z");
    @Mock private NotificationStore store;
    @Mock private NotificationRecipientDirectory directory;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(
                store, directory, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deliversToActiveRecipient() {
        UUID recipientId = UUID.randomUUID();
        NotificationDefinition definition = definition(recipientId);
        when(directory.findActiveById(recipientId)).thenReturn(Optional.of(
                new NotificationRecipient(recipientId, "admin", Set.of("ADMIN"))));
        NotificationDetails expected = new NotificationDetails(
                UUID.randomUUID(), recipientId, NotificationType.SYSTEM,
                NotificationSeverity.INFO, "System notice", "System information.",
                null, null, null, NOW, NOW, 0L);
        when(store.create(definition, NOW)).thenReturn(expected);

        assertThat(service.deliver(definition)).isSameAs(expected);
        verify(store).create(definition, NOW);
    }

    @Test
    void rejectsInactiveRecipientWithoutCreatingNotification() {
        UUID recipientId = UUID.randomUUID();
        NotificationDefinition definition = definition(recipientId);
        when(directory.findActiveById(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deliver(definition))
                .isInstanceOf(NotificationRecipientUnavailableException.class);
        verify(store, never()).create(any(), any());
    }

    private static NotificationDefinition definition(UUID recipientId) {
        return new NotificationDefinition(
                recipientId, NotificationType.SYSTEM, NotificationSeverity.INFO,
                "System notice", "System information.", null, null);
    }
}
