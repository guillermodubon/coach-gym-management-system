package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event describing only the presence transition of a photo. */
public record StaffProfilePhotoChanged(
        UUID userId,
        boolean photoPresent,
        Instant occurredAt,
        String actorIdentifier) {

    public StaffProfilePhotoChanged(
            UUID userId,
            boolean photoPresent,
            Instant occurredAt) {
        this(userId, photoPresent, occurredAt, null);
    }

    public StaffProfilePhotoChanged {
        if (userId == null || occurredAt == null) {
            throw new IllegalArgumentException("Staff profile photo event is invalid.");
        }
        actorIdentifier = actorIdentifier == null || actorIdentifier.isBlank()
                ? "staff" : actorIdentifier.strip();
    }
}
