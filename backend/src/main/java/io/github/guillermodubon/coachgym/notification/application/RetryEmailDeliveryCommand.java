package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.UUID;

/** Explicit, versioned request to retry one failed canonical delivery. */
public record RetryEmailDeliveryCommand(UUID deliveryId, long expectedVersion) {

    public RetryEmailDeliveryCommand {
        if (deliveryId == null) {
            throw new EmailDeliveryValidationException("Email delivery id is required.");
        }
        if (expectedVersion < 0) {
            throw new EmailDeliveryValidationException(
                    "Email delivery expected version must not be negative.");
        }
    }
}
