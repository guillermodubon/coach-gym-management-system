package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.util.Objects;

/** Immutable result of rendering one versioned transactional email template. */
public record ComposedEmail(String templateVersion, EmailMessage message) {

    public ComposedEmail {
        templateVersion = EmailDeliveryValuePolicy.normalizeTemplateVersion(templateVersion);
        message = Objects.requireNonNull(message, "Composed email message is required.");
    }
}
