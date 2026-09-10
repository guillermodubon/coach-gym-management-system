package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.util.UUID;

/** Most recent access attempt displayed in the client profile. */
public record ClientAccessSummary(
        UUID accessRecordId,
        String decision,
        String reasonCode,
        Instant occurredAt) {

    public ClientAccessSummary {
        if (accessRecordId == null) {
            throw new IllegalArgumentException("Access record id is required.");
        }
        decision = required(decision, "Access decision");
        reasonCode = required(reasonCode, "Access reason code");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Access timestamp is required.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
