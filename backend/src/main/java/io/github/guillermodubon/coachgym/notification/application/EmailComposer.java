package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.ComposedEmail;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;

/** Application port for rendering a safe, versioned transactional email. */
public interface EmailComposer {

    /** Composes a message from server-owned source data only. */
    ComposedEmail compose(EmailDeliverySource source);
}
