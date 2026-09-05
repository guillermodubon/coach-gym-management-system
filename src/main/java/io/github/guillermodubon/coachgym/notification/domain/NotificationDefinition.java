package io.github.guillermodubon.coachgym.notification.domain;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import java.util.UUID;

/** Validated definition used to create an unread internal notification. */
public record NotificationDefinition(
        UUID recipientUserId,
        NotificationType notificationType,
        NotificationSeverity severity,
        NotificationContent content,
        NotificationReference reference) {

    public NotificationDefinition {
        if (recipientUserId == null) {
            throw new NotificationValidationException(
                    "Notification recipient user id is required.");
        }
        if (notificationType == null) {
            throw new NotificationValidationException(
                    "Notification type is required.");
        }
        if (severity == null) {
            throw new NotificationValidationException(
                    "Notification severity is required.");
        }
        if (content == null) {
            throw new NotificationValidationException(
                    "Notification content is required.");
        }
        if (reference == null) {
            reference = NotificationReference.none();
        }
    }

    public NotificationDefinition(
            UUID recipientUserId,
            NotificationType notificationType,
            NotificationSeverity severity,
            String title,
            String body,
            NotificationResourceType resourceType,
            UUID resourceId) {
        this(
                recipientUserId,
                notificationType,
                severity,
                new NotificationContent(title, body),
                new NotificationReference(resourceType, resourceId));
    }
}
