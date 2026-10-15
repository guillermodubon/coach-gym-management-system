package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.Objects;
import java.util.UUID;

/** Command used to change the priority of an incident. */
public record ChangeIncidentPriorityCommand(
        UUID incidentId,
        IncidentPriority priority,
        String reason,
        long version) {

    public static final int MAX_REASON_LENGTH = 2_000;

    public ChangeIncidentPriorityCommand {
        Objects.requireNonNull(incidentId, "Incident id is required.");
        Objects.requireNonNull(priority, "Incident priority is required.");
        reason = normalizeRequired(reason);
        if (version < 0) {
            throw new IncidentValidationException(
                    "Incident version cannot be negative.");
        }
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IncidentValidationException(
                    "Priority-change reason is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IncidentValidationException(
                    "Priority-change reason must not exceed 2000 characters.");
        }
        return normalized;
    }
}
