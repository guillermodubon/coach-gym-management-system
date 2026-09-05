package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Creates administrator alerts for high-impact equipment incidents. */
@Component
public class IncidentNotificationEventListener {

    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationRecipientDirectory recipientDirectory;
    private final NotificationDeliveryService deliveryService;

    public IncidentNotificationEventListener(
            NotificationRecipientDirectory recipientDirectory,
            NotificationDeliveryService deliveryService) {
        this.recipientDirectory = recipientDirectory;
        this.deliveryService = deliveryService;
    }

    @EventListener
    public void on(IncidentReportedEvent event) {
        if (event.priority() != IncidentPriority.HIGH
                && event.priority() != IncidentPriority.CRITICAL) {
            return;
        }

        NotificationSeverity severity = event.priority() == IncidentPriority.CRITICAL
                ? NotificationSeverity.CRITICAL
                : NotificationSeverity.WARNING;
        String title = event.priority() == IncidentPriority.CRITICAL
                ? "Critical equipment incident reported"
                : "High-priority equipment incident reported";
        String equipmentReference = event.equipmentCode() == null
                ? event.equipmentId().toString()
                : event.equipmentCode();
        String body = "Incident " + event.incidentCode()
                + " was reported for equipment " + equipmentReference + ".";

        Set<UUID> deliveredRecipients = new LinkedHashSet<>();
        for (NotificationRecipient recipient
                : recipientDirectory.findActiveByRole(ADMIN_ROLE)) {
            if (recipient.userId().equals(event.actorUserId())) {
                continue;
            }
            if (!deliveredRecipients.add(recipient.userId())) {
                continue;
            }
            deliveryService.deliver(new NotificationDefinition(
                    recipient.userId(),
                    NotificationType.INCIDENT_ASSIGNED,
                    severity,
                    title,
                    body,
                    NotificationResourceType.INCIDENT,
                    event.incidentId()));
        }
    }
}
