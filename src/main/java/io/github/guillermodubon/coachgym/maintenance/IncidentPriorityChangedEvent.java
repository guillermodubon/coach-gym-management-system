package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published when an administrator changes incident priority. */
public record IncidentPriorityChangedEvent(UUID incidentId, String incidentCode,
        IncidentPriority previousPriority, IncidentPriority newPriority, String reason,
        UUID actorUserId, String actorIdentifier, Instant occurredAt) {
    public static final int MAX_REASON_LENGTH = 2_000;
    public IncidentPriorityChangedEvent {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(previousPriority, "Previous priority is required.");
        Objects.requireNonNull(newPriority, "New priority is required.");
        Objects.requireNonNull(actorUserId, "Event actor is required.");
        Objects.requireNonNull(occurredAt, "Event timestamp is required.");
        if (previousPriority == newPriority) throw new IllegalArgumentException("Previous and new priorities must differ.");
        incidentCode = required(incidentCode, "Incident code is required.");
        reason = required(reason, "Priority-change reason is required.");
        actorIdentifier = required(actorIdentifier, "Actor identifier is required.");
        if (reason.length() > MAX_REASON_LENGTH) throw new IllegalArgumentException("Priority-change reason must not exceed " + MAX_REASON_LENGTH + " characters.");
    }
    private static String required(String v, String m) { if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(m); return v.trim(); }
}
