package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event for a successful password change. */
public record StaffPasswordChanged(
        UUID userId,
        boolean reauthenticationRequired,
        Instant occurredAt,
        String actorIdentifier) {

    public StaffPasswordChanged(
            UUID userId,
            boolean reauthenticationRequired,
            Instant occurredAt) {
        this(userId, reauthenticationRequired, occurredAt, null);
    }

    public StaffPasswordChanged {
        if (userId == null || occurredAt == null) {
            throw new IllegalArgumentException("Staff password event is invalid.");
        }
        actorIdentifier = actorIdentifier == null || actorIdentifier.isBlank()
                ? "staff" : actorIdentifier.strip();
    }
}
