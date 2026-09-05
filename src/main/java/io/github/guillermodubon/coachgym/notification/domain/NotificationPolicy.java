package io.github.guillermodubon.coachgym.notification.domain;

import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;

/** Cross-field policy for supported notification categories. */
public final class NotificationPolicy {

    public void validate(NotificationDefinition definition) {
        if (definition == null) {
            throw new NotificationValidationException(
                    "Notification definition is required.");
        }

        validateReference(definition);
        validateSeverity(definition.notificationType(), definition.severity());
    }

    private static void validateReference(NotificationDefinition definition) {
        if (definition.notificationType() == NotificationType.SYSTEM
                && definition.reference().present()) {
            throw new NotificationValidationException(
                    "System notifications must not reference a business resource.");
        }
    }

    private static void validateSeverity(
            NotificationType type,
            NotificationSeverity severity) {
        if (type == NotificationType.MEMBERSHIP_EXPIRING
                && severity == NotificationSeverity.CRITICAL) {
            throw new NotificationValidationException(
                    "Membership expiration notifications cannot be critical.");
        }
    }
}
