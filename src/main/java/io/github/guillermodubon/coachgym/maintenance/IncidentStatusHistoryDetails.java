package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable public projection of one append-only incident status change. */
public record IncidentStatusHistoryDetails(
        UUID id,
        UUID incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId) {

    public IncidentStatusHistoryDetails {
        Objects.requireNonNull(id, "Incident status-history id is required.");
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(newStatus, "New incident status is required.");
        Objects.requireNonNull(occurredAt, "Status transition timestamp is required.");
        Objects.requireNonNull(changedByUserId, "Status transition actor is required.");
        reason = normalizeRequired(reason);
        if (previousStatus == newStatus) throw new IllegalArgumentException("Previous and new incident statuses must differ.");
        if (previousStatus == null && newStatus != IncidentStatus.OPEN) {
            throw new IllegalArgumentException("An initial status-history entry must create OPEN.");
        }
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Status transition reason is required.");
        String normalized = value.trim();
        if (normalized.length() > 2_000) throw new IllegalArgumentException("Status transition reason must not exceed 2000 characters.");
        return normalized;
    }
}
