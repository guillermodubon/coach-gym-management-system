package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.Objects;
import java.util.UUID;

/** Command used to start investigating an open incident. */
public record StartIncidentInvestigationCommand(
        UUID incidentId,
        String reason,
        long version) {

    public static final int MAX_REASON_LENGTH = 2_000;

    public StartIncidentInvestigationCommand {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        reason = normalizeRequired(reason);
        validateVersion(version);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IncidentValidationException(
                    "Investigation reason is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IncidentValidationException(
                    "Investigation reason must not exceed 2000 characters.");
        }
        return normalized;
    }

    private static void validateVersion(long value) {
        if (value < 0) {
            throw new IncidentValidationException(
                    "Incident version cannot be negative.");
        }
    }
}
