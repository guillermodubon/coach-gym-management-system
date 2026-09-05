package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.util.Locale;

/** Allowlisted sort fields for notification inbox queries. */
public enum NotificationSortField {
    CREATED_AT,
    SEVERITY,
    NOTIFICATION_TYPE,
    READ_AT,
    ID;

    public static NotificationSortField from(String value) {
        if (value == null || value.isBlank()) {
            return CREATED_AT;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new NotificationValidationException(
                    "Unsupported notification sort field: " + value + ".");
        }
    }
}
