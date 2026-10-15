package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.Objects;
import java.util.UUID;

/** Command used to resolve an incident under investigation. */
public record ResolveIncidentCommand(
        UUID incidentId,
        String resolutionNotes,
        long version) {

    public static final int MAX_RESOLUTION_NOTES_LENGTH = 2_000;

    public ResolveIncidentCommand {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        resolutionNotes = normalizeRequired(resolutionNotes);
        if (version < 0) {
            throw new IncidentValidationException(
                    "Incident version cannot be negative.");
        }
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IncidentValidationException(
                    "Resolution notes are required.");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_RESOLUTION_NOTES_LENGTH) {
            throw new IncidentValidationException(
                    "Resolution notes must not exceed 2000 characters.");
        }
        return normalized;
    }
}
