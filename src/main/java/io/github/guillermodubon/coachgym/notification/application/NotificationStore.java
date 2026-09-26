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

    default Optional<NotificationDetails> findByIdAndRecipientUserId(
            UUID notificationId, UUID recipientUserId, UUID branchId) {
        return findByIdAndRecipientUserId(notificationId, recipientUserId);
    }

    NotificationPage findAllByRecipientUserId(
            UUID recipientUserId, NotificationSearchQuery query);

    default NotificationPage findAllByRecipientUserId(
            UUID recipientUserId, NotificationSearchQuery query, UUID branchId) {
        return findAllByRecipientUserId(recipientUserId, query);
    }

    long countUnreadByRecipientUserId(UUID recipientUserId);

    default long countUnreadByRecipientUserId(UUID recipientUserId, UUID branchId) {
        return countUnreadByRecipientUserId(recipientUserId);
    }

    NotificationDetails markAsRead(
            UUID notificationId, UUID recipientUserId, Instant readAt);

    default NotificationDetails markAsRead(
            UUID notificationId, UUID recipientUserId, Instant readAt, UUID branchId) {
        return markAsRead(notificationId, recipientUserId, readAt);
    }

    int markAllAsRead(UUID recipientUserId, Instant readAt);

    default int markAllAsRead(UUID recipientUserId, Instant readAt, UUID branchId) {
        return markAllAsRead(recipientUserId, readAt);
    }
}
