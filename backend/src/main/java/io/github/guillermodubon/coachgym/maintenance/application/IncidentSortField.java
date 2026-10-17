package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.Locale;

/** Allowlisted fields available for incident sorting. */
public enum IncidentSortField {
    REPORTED_AT,
    PRIORITY,
    STATUS,
    INCIDENT_CODE,
    UPDATED_AT,
    ID;

    public static IncidentSortField from(String value) {
        if (value == null || value.isBlank()) {
            return REPORTED_AT;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IncidentValidationException(
                    "Unsupported incident sort field: " + value + ".");
        }
    }
}
