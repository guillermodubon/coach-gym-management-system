package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.util.Locale;

/** Allowlisted fields supported by maintenance list sorting. */
public enum MaintenanceSortField {
    SCHEDULED_ON,
    STATUS,
    MAINTENANCE_TYPE,
    MAINTENANCE_CODE,
    CREATED_AT,
    UPDATED_AT,
    ID;

    public static MaintenanceSortField from(String value) {
        if (value == null || value.isBlank()) {
            return SCHEDULED_ON;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MaintenanceValidationException(
                    "Unsupported maintenance sort field: " + value + ".");
        }
    }
}
