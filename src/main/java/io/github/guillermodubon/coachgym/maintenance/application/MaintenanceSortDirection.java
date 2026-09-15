package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.util.Locale;

/** Allowlisted sort directions for maintenance queries. */
public enum MaintenanceSortDirection {
    ASC,
    DESC;

    public static MaintenanceSortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MaintenanceValidationException(
                    "Unsupported maintenance sort direction: " + value + ".");
        }
    }
}
