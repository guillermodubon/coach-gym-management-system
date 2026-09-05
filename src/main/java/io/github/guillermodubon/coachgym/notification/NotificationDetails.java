package io.github.guillermodubon.coachgym.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read model for one internal user notification.
 *
 * <p>The recipient is authoritative. Consumers must never expose a notification
 * without constraining the lookup to the authenticated recipient.</p>
 */
public record NotificationDetails(
        UUID id,
        UUID recipientUserId,
        NotificationType notificationType,
        NotificationSeverity severity,
        String title,
        String body,
        NotificationResourceType resourceType,
        UUID resourceId,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    /** Returns whether the notification has already been read. */
    public boolean read() {
        return readAt != null;
    }
}
