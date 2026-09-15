package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable public projection of an equipment incident. */
public record IncidentDetails(
        UUID id,
        long incidentNumber,
        String incidentCode,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        IncidentStatus status,
        IncidentPriority priority,
        String description,
        Instant reportedAt,
        UUID reportedByUserId,
        UUID assignedToUserId,
        Instant resolvedAt,
        UUID resolvedByUserId,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public IncidentDetails {
        Objects.requireNonNull(id, "Incident id is required.");
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(status, "Incident status is required.");
        Objects.requireNonNull(priority, "Incident priority is required.");
        Objects.requireNonNull(reportedAt, "Incident reported-at timestamp is required.");
        Objects.requireNonNull(reportedByUserId, "Incident reporter is required.");
        Objects.requireNonNull(createdAt, "Incident created-at timestamp is required.");
        Objects.requireNonNull(updatedAt, "Incident updated-at timestamp is required.");
        if (incidentNumber < 0) throw new IllegalArgumentException("Incident number cannot be negative.");
        if (version < 0) throw new IllegalArgumentException("Incident version cannot be negative.");
        incidentCode = normalizeOptional(incidentCode);
        equipmentCode = normalizeOptional(equipmentCode);
        equipmentName = normalizeOptional(equipmentName);
        description = requireText(description, "Incident description is required.");
        resolutionNotes = normalizeOptional(resolutionNotes);
        validateResolutionState(status, resolvedAt, resolvedByUserId, resolutionNotes);
    }

    private static void validateResolutionState(IncidentStatus status, Instant resolvedAt,
            UUID resolvedByUserId, String resolutionNotes) {
        if (status == IncidentStatus.RESOLVED) {
            Objects.requireNonNull(resolvedAt, "Resolved incidents require a resolved-at timestamp.");
            Objects.requireNonNull(resolvedByUserId, "Resolved incidents require a resolving user.");
            if (resolutionNotes == null) throw new IllegalArgumentException("Resolved incidents require resolution notes.");
            return;
        }
        if (resolvedAt != null || resolvedByUserId != null || resolutionNotes != null) {
            throw new IllegalArgumentException("Unresolved incidents cannot contain resolution data.");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
