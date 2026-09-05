package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Raised when a notification is not visible to the requested recipient. */
public class NotificationNotFoundException extends RuntimeException {

    private final UUID notificationId;

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification was not found.");
        this.notificationId = notificationId;
    }

    public UUID notificationId() {
        return notificationId;
    }
}
