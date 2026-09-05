package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID recipientUserId,
        NotificationType notificationType,
        NotificationSeverity severity,
        String title,
        String body,
        NotificationResourceType resourceType,
        UUID resourceId,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static NotificationResponse from(NotificationDetails details) {
        return new NotificationResponse(
                details.id(),
                details.recipientUserId(),
                details.notificationType(),
                details.severity(),
                details.title(),
                details.body(),
                details.resourceType(),
                details.resourceId(),
                details.read(),
                details.readAt(),
                details.createdAt(),
                details.updatedAt(),
                details.version());
    }
}
