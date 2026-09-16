package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.UUID;

/** Server-authorized request to deliver a client's canonical access credential. */
public record RequestAccessCredentialEmailCommand(UUID clientId) {

    public RequestAccessCredentialEmailCommand {
        if (clientId == null) {
            throw new EmailDeliveryValidationException("Client id is required.");
        }
    }
}
