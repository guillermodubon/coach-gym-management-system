package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import java.util.Objects;

/** Validated request to transition an incident between lifecycle states. */
public record IncidentStatusTransition(
        IncidentStatus previousStatus,
        IncidentStatus resultingStatus,
        String reason) {

    public static final int MAX_REASON_LENGTH = 2_000;

    public IncidentStatusTransition {
        Objects.requireNonNull(previousStatus, "Previous incident status is required.");
        Objects.requireNonNull(resultingStatus, "Resulting incident status is required.");
        reason = normalizeRequiredReason(reason);
        if (previousStatus == resultingStatus) {
            throw new IncidentValidationException("An incident cannot transition to its current status.");
        }
    }

    private static String normalizeRequiredReason(String value) {
        if (value == null || value.trim().isEmpty()) throw new IncidentValidationException("Incident transition reason is required.");
        String normalized = value.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IncidentValidationException("Incident transition reason must not exceed " + MAX_REASON_LENGTH + " characters.");
        }
        return normalized;
    }
}
