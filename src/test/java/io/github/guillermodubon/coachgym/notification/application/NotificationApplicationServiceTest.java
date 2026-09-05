package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T15:00:00Z");
    @Mock private NotificationStore store;
    private NotificationApplicationService service;
    private AuthenticatedActor actor;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(
                store, Clock.fixed(NOW, ZoneOffset.UTC));
        actor = new AuthenticatedActor(UUID.randomUUID(), "inbox-user");
    }

    @Test
    void listsOnlyAuthenticatedRecipientsNotifications() {
        NotificationSearchQuery query = NotificationSearchQuery.defaults();
        NotificationPage expected = new NotificationPage(List.of(), 0, 25, 0, 0);
        when(store.findAllByRecipientUserId(actor.id(), query)).thenReturn(expected);

        assertThat(service.findAll(query, actor)).isSameAs(expected);
        verify(store).findAllByRecipientUserId(actor.id(), query);
    }

    @Test
    void hidesNotificationMissingForAuthenticatedRecipient() {
        UUID id = UUID.randomUUID();
        when(store.findByIdAndRecipientUserId(id, actor.id()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, actor))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void marksOneNotificationAsReadUsingApplicationClock() {
        UUID id = UUID.randomUUID();
        NotificationDetails details = details(id, actor.id(), NOW);
        when(store.markAsRead(id, actor.id(), NOW)).thenReturn(details);

        assertThat(service.markAsRead(id, actor).read()).isTrue();
        verify(store).markAsRead(id, actor.id(), NOW);
    }

    @Test
    void marksAllAndReturnsCurrentUnreadCount() {
        when(store.countUnreadByRecipientUserId(actor.id())).thenReturn(0L);

        assertThat(service.markAllAsRead(actor).count()).isZero();
        verify(store).markAllAsRead(actor.id(), NOW);
        verify(store).countUnreadByRecipientUserId(actor.id());
    }

    private static NotificationDetails details(
            UUID id, UUID recipient, Instant readAt) {
        return new NotificationDetails(
                id, recipient, NotificationType.SYSTEM,
                NotificationSeverity.INFO, "System notice", "System information.",
                null, null, readAt, NOW, NOW, 1L);
    }
}
