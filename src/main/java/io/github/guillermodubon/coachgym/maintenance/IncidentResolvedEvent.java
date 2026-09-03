package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published after an incident has been resolved successfully. */
public record IncidentResolvedEvent(UUID incidentId, String incidentCode, UUID equipmentId,
        String resolutionNotes, UUID actorUserId, String actorIdentifier, Instant occurredAt) {
    public static final int MAX_RESOLUTION_NOTES_LENGTH = 2_000;
    public IncidentResolvedEvent {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(actorUserId, "Event actor is required.");
        Objects.requireNonNull(occurredAt, "Event timestamp is required.");
        incidentCode = required(incidentCode, "Incident code is required.");
        resolutionNotes = required(resolutionNotes, "Resolution notes are required.");
        actorIdentifier = required(actorIdentifier, "Actor identifier is required.");
        if (resolutionNotes.length() > MAX_RESOLUTION_NOTES_LENGTH) throw new IllegalArgumentException("Resolution notes must not exceed " + MAX_RESOLUTION_NOTES_LENGTH + " characters.");
    }
    private static String required(String v, String m) { if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(m); return v.trim(); }
}
