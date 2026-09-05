package io.github.guillermodubon.coachgym.notification.domain;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import java.util.UUID;

/** Optional reference from a notification to a public application resource. */
public record NotificationReference(
        NotificationResourceType resourceType,
        UUID resourceId) {

    public NotificationReference {
        boolean typePresent = resourceType != null;
        boolean idPresent = resourceId != null;
        if (typePresent != idPresent) {
            throw new NotificationValidationException(
                    "Notification resource type and resource id must be provided together.");
        }
    }

    public static NotificationReference none() {
        return new NotificationReference(null, null);
    }

    public boolean present() {
        return resourceType != null;
    }
}
