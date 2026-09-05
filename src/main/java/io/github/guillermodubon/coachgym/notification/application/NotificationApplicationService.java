package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationUnreadCount;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recipient-scoped application service for the internal notification inbox. */
@Service
public class NotificationApplicationService {

    private final NotificationStore notificationStore;
    private final Clock clock;

    public NotificationApplicationService(
            NotificationStore notificationStore,
            Clock clock) {
        this.notificationStore = Objects.requireNonNull(
                notificationStore, "Notification store is required.");
        this.clock = Objects.requireNonNull(clock, "Application clock is required.");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationPage findAll(
            NotificationSearchQuery query,
            AuthenticatedActor actor) {
        requireActor(actor);
        return notificationStore.findAllByRecipientUserId(
                actor.id(),
                Objects.requireNonNull(query, "Notification search query is required."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationDetails findById(
            UUID notificationId,
            AuthenticatedActor actor) {
        requireId(notificationId);
        requireActor(actor);
        return notificationStore.findByIdAndRecipientUserId(notificationId, actor.id())
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationUnreadCount countUnread(AuthenticatedActor actor) {
        requireActor(actor);
        return new NotificationUnreadCount(
                notificationStore.countUnreadByRecipientUserId(actor.id()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationDetails markAsRead(
            UUID notificationId,
            AuthenticatedActor actor) {
        requireId(notificationId);
        requireActor(actor);
        return notificationStore.markAsRead(notificationId, actor.id(), now());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationUnreadCount markAllAsRead(AuthenticatedActor actor) {
        requireActor(actor);
        notificationStore.markAllAsRead(actor.id(), now());
        return new NotificationUnreadCount(
                notificationStore.countUnreadByRecipientUserId(actor.id()));
    }

    private static void requireId(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification id is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (actor.id() == null) {
            throw new IllegalArgumentException("Authenticated actor id is required.");
        }
    }

    private Instant now() {
        return clock.instant();
    }
}
