package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.UUID;

/** Server-authorized request to deliver the canonical payment receipt email. */
public record RequestPaymentReceiptEmailCommand(UUID paymentId) {

    public RequestPaymentReceiptEmailCommand {
        if (paymentId == null) {
            throw new EmailDeliveryValidationException("Payment id is required.");
        }
    }
}
