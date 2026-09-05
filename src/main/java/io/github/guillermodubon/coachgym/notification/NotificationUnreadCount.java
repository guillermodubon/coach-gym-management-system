package io.github.guillermodubon.coachgym.notification;

/** Number of unread notifications in the authenticated user's inbox. */
public record NotificationUnreadCount(long count) {

    public NotificationUnreadCount {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "Unread notification count must not be negative.");
        }
    }
}
