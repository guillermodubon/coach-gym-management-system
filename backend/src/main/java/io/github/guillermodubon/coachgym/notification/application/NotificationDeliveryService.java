package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import io.github.guillermodubon.coachgym.notification.domain.NotificationPolicy;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal delivery boundary used by event listeners, never exposed as an HTTP command. */
@Service
public class NotificationDeliveryService {

    private final NotificationStore notificationStore;
    private final NotificationRecipientDirectory recipientDirectory;
    private final NotificationPolicy policy;
    private final Clock clock;

    public NotificationDeliveryService(
            NotificationStore notificationStore,
            NotificationRecipientDirectory recipientDirectory,
            Clock clock) {
        this.notificationStore = Objects.requireNonNull(
                notificationStore, "Notification store is required.");
        this.recipientDirectory = Objects.requireNonNull(
                recipientDirectory, "Notification recipient directory is required.");
        this.policy = new NotificationPolicy();
        this.clock = Objects.requireNonNull(clock, "Application clock is required.");
    }

    @Transactional
    public NotificationDetails deliver(NotificationDefinition definition) {
        policy.validate(definition);
        recipientDirectory.findActiveById(definition.recipientUserId())
                .orElseThrow(() -> new NotificationRecipientUnavailableException(
                        definition.recipientUserId()));
        return notificationStore.create(definition, clock.instant());
    }
}
