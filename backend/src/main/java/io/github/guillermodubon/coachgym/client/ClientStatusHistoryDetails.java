package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.util.UUID;

/** Append-only client lifecycle history projection. */
public record ClientStatusHistoryDetails(
        UUID id,
        UUID clientId,
        ClientStatus previousStatus,
        ClientStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId) {

    public ClientStatusHistoryDetails {
        if (id == null || clientId == null || newStatus == null
                || occurredAt == null || changedByUserId == null) {
            throw new IllegalArgumentException(
                    "Client status history required fields are missing.");
        }
        if (previousStatus != null && previousStatus == newStatus) {
            throw new IllegalArgumentException(
                    "Client status history must represent a real transition.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Client status history reason is required.");
        }
        reason = reason.strip();
    }
}
