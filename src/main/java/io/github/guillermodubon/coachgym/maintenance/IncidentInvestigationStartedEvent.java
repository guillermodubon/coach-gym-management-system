package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published when an administrator starts investigating an open incident. */
public record IncidentInvestigationStartedEvent(UUID incidentId, String incidentCode,
        UUID equipmentId, UUID actorUserId, String actorIdentifier, Instant occurredAt) {
    public IncidentInvestigationStartedEvent {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(actorUserId, "Event actor is required.");
        Objects.requireNonNull(occurredAt, "Event timestamp is required.");
        incidentCode = required(incidentCode, "Incident code is required.");
        actorIdentifier = required(actorIdentifier, "Actor identifier is required.");
    }
    private static String required(String v, String m) { if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(m); return v.trim(); }
}
