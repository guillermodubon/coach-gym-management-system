package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Raised when an internal notification targets a user who is not active. */
public class NotificationRecipientUnavailableException extends RuntimeException {

    private final UUID recipientUserId;

    public NotificationRecipientUnavailableException(UUID recipientUserId) {
        super("Notification recipient is not active or does not exist.");
        this.recipientUserId = recipientUserId;
    }

    public UUID recipientUserId() {
        return recipientUserId;
    }
}
