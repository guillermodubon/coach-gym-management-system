package io.github.guillermodubon.coachgym.notification.application;

import java.util.Set;
import java.util.UUID;

/** Minimal active-user projection used for notification routing. */
public record NotificationRecipient(
        UUID userId,
        String username,
        Set<String> roles) {

    public NotificationRecipient {
        if (userId == null) {
            throw new IllegalArgumentException("Notification recipient user id is required.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Notification recipient username is required.");
        }
        username = username.strip();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
