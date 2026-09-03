package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import java.util.Objects;
import java.util.UUID;

/** Validated definition used to report an equipment incident. */
public record IncidentDefinition(
        UUID equipmentId,
        IncidentPriority priority,
        String description,
        boolean takeOutOfService,
        Long equipmentVersion) {

    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    public IncidentDefinition {
        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(priority, "Incident priority is required.");
        description = normalizeRequiredDescription(description);
        if (takeOutOfService && equipmentVersion == null) {
            throw new IncidentValidationException("Equipment version is required when taking equipment out of service.");
        }
        if (equipmentVersion != null && equipmentVersion < 0) {
            throw new IncidentValidationException("Equipment version cannot be negative.");
        }
    }

    private static String normalizeRequiredDescription(String value) {
        if (value == null || value.trim().isEmpty()) throw new IncidentValidationException("Incident description is required.");
        String normalized = value.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IncidentValidationException("Incident description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return normalized;
    }
}
