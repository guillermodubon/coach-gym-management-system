package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.util.Locale;

/** Allowlisted read-state filter for a notification inbox query. */
public enum NotificationReadFilter {
    ALL,
    UNREAD,
    READ;

    public static NotificationReadFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new NotificationValidationException(
                    "Unsupported notification read filter: " + value + ".");
        }
    }
}
