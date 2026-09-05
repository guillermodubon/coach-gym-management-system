package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.util.Locale;

/** Supported sort directions for notification inbox queries. */
public enum NotificationSortDirection {
    ASC,
    DESC;

    public static NotificationSortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new NotificationValidationException(
                    "Unsupported notification sort direction: " + value + ".");
        }
    }
}
