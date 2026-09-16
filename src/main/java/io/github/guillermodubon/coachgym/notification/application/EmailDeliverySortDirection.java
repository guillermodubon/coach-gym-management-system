package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.Locale;

/** Supported delivery ordering directions. */
public enum EmailDeliverySortDirection {
    ASC,
    DESC;

    public static EmailDeliverySortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new EmailDeliveryValidationException(
                    "Unsupported email delivery sort direction.");
        }
    }
}
