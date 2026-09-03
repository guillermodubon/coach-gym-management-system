package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published after an equipment incident has been successfully persisted. */
public record IncidentReportedEvent(UUID incidentId, String incidentCode, UUID equipmentId,
        String equipmentCode, IncidentPriority priority, boolean takenOutOfService,
        UUID actorUserId, String actorIdentifier, Instant occurredAt) {
    public IncidentReportedEvent {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(priority, "Incident priority is required.");
        Objects.requireNonNull(actorUserId, "Event actor is required.");
        Objects.requireNonNull(occurredAt, "Event timestamp is required.");
        incidentCode = required(incidentCode, "Incident code is required.");
        equipmentCode = optional(equipmentCode);
        actorIdentifier = required(actorIdentifier, "Actor identifier is required.");
    }
    private static String required(String v, String m) { String n = optional(v); if (n == null) throw new IllegalArgumentException(m); return n; }
    private static String optional(String v) { if (v == null) return null; String n = v.trim(); return n.isEmpty() ? null : n; }
}
