package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Privacy-safe event describing a meaningful self-profile field update. */
public record StaffProfileUpdated(
        UUID userId,
        Set<String> changedFields,
        Instant occurredAt,
        String actorIdentifier) {

    public StaffProfileUpdated(
            UUID userId,
            Set<String> changedFields,
            Instant occurredAt) {
        this(userId, changedFields, occurredAt, null);
    }

    public StaffProfileUpdated {
        if (userId == null || changedFields == null || changedFields.isEmpty()
                || occurredAt == null) {
            throw new IllegalArgumentException("Staff profile update event is invalid.");
        }
        changedFields = Set.copyOf(changedFields);
        actorIdentifier = normalizeActorIdentifier(actorIdentifier);
        if (!changedFields.stream().allMatch(StaffSelfProfilePolicy::isEditable)) {
            throw new IllegalArgumentException(
                    "Staff profile update event contains an unsupported field.");
        }
    }

    private static String normalizeActorIdentifier(String value) {
        return value == null || value.isBlank() ? "staff" : value.strip();
    }
}
