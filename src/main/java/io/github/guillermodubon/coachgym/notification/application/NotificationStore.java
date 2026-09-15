package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for delivery and recipient-scoped inbox operations. */
public interface NotificationStore {

    NotificationDetails create(NotificationDefinition definition, Instant createdAt);

    Optional<NotificationDetails> findByIdAndRecipientUserId(
            UUID notificationId, UUID recipientUserId);

    NotificationPage findAllByRecipientUserId(
            UUID recipientUserId, NotificationSearchQuery query);

    long countUnreadByRecipientUserId(UUID recipientUserId);

    NotificationDetails markAsRead(
            UUID notificationId, UUID recipientUserId, Instant readAt);

    int markAllAsRead(UUID recipientUserId, Instant readAt);
}
