package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.Locale;

/** Allowlisted delivery sort fields; values are never interpolated from HTTP. */
public enum EmailDeliverySortField {
    REQUESTED_AT,
    UPDATED_AT,
    STATUS,
    DELIVERY_TYPE,
    ID;

    public static EmailDeliverySortField from(String value) {
        if (value == null || value.isBlank()) {
            return REQUESTED_AT;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new EmailDeliveryValidationException(
                    "Unsupported email delivery sort field.");
        }
    }
}
