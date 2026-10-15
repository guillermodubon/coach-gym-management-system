package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.Locale;

/** Supported sort directions for incident searches. */
public enum IncidentSortDirection {
    ASC,
    DESC;

    public static IncidentSortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IncidentValidationException(
                    "Unsupported incident sort direction: " + value + ".");
        }
    }
}
