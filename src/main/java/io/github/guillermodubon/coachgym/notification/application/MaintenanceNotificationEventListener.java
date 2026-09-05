package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationLookup;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceUpdatedEvent;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Delivers recipient-scoped alerts for maintenance work-order lifecycle events. */
@Component
public class MaintenanceNotificationEventListener {

    private final MaintenanceNotificationLookup maintenanceLookup;
    private final NotificationDeliveryService deliveryService;

    public MaintenanceNotificationEventListener(
            MaintenanceNotificationLookup maintenanceLookup,
            NotificationDeliveryService deliveryService) {
        this.maintenanceLookup = maintenanceLookup;
        this.deliveryService = deliveryService;
    }

    @EventListener
    public void on(MaintenanceScheduledEvent event) {
        MaintenanceNotificationDetails details =
                requireDetails(event.maintenanceId());

        deliverTo(
                recipientSet(details.assignedToUserId()),
                event.actorUserId(),
                NotificationSeverity.INFO,
                "Maintenance assigned",
                message(
                        "was scheduled and assigned",
                        details),
                details);
    }

    @EventListener
    public void on(MaintenanceUpdatedEvent event) {
        MaintenanceNotificationDetails details =
                requireDetails(event.maintenanceId());

        deliverTo(
                recipientSet(details.assignedToUserId()),
                event.actorUserId(),
                NotificationSeverity.INFO,
                "Assigned maintenance updated",
                message(
                        "was updated",
                        details),
                details);
    }

    @EventListener
    public void on(MaintenanceStartedEvent event) {
        MaintenanceNotificationDetails details =
                requireDetails(event.maintenanceId());

        deliverTo(
                recipientSet(details.createdByUserId()),
                event.actorUserId(),
                NotificationSeverity.INFO,
                "Maintenance started",
                message(
                        "was started",
                        details),
                details);
    }

    @EventListener
    public void on(MaintenanceCompletedEvent event) {
        MaintenanceNotificationDetails details =
                requireDetails(event.maintenanceId());

        NotificationSeverity severity =
                event.equipmentOutcome()
                        == EquipmentMaintenanceOutcome.OUT_OF_SERVICE
                        ? NotificationSeverity.WARNING
                        : NotificationSeverity.INFO;

        String action =
                event.equipmentOutcome()
                        == EquipmentMaintenanceOutcome.OUT_OF_SERVICE
                        ? "was completed, but the equipment remains out of service"
                        : "was completed and the equipment is available";

        deliverTo(
                recipientSet(details.createdByUserId()),
                event.actorUserId(),
                severity,
                "Maintenance completed",
                message(action, details),
                details);
    }

    @EventListener
    public void on(MaintenanceCancelledEvent event) {
        MaintenanceNotificationDetails details = requireDetails(event.maintenanceId());
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (details.createdByUserId() != null) {
            recipients.add(details.createdByUserId());
        }
        if (details.assignedToUserId() != null) {
            recipients.add(details.assignedToUserId());
        }
        deliverTo(
                recipients,
                event.actorUserId(),
                NotificationSeverity.WARNING,
                "Maintenance cancelled",
                message("was cancelled", details),
                details);
    }

    private MaintenanceNotificationDetails requireDetails(UUID maintenanceId) {
        return maintenanceLookup.findById(maintenanceId)
                .orElseThrow(() -> new MaintenanceNotificationUnavailableException(
                        maintenanceId));
    }

    private void deliverTo(
            Set<UUID> recipients,
            UUID actorUserId,
            NotificationSeverity severity,
            String title,
            String body,
            MaintenanceNotificationDetails details) {

        for (UUID recipientId : recipients) {
            if (recipientId == null
                    || recipientId.equals(actorUserId)) {
                continue;
            }

            deliveryService.deliver(
                    new NotificationDefinition(
                            recipientId,
                            NotificationType.MAINTENANCE_ASSIGNED,
                            severity,
                            title,
                            body,
                            NotificationResourceType.MAINTENANCE,
                            details.maintenanceId()));
        }
    }

    private static String message(
            String action,
            MaintenanceNotificationDetails details) {
        String maintenanceReference = details.maintenanceCode() == null
                ? details.maintenanceId().toString()
                : details.maintenanceCode();
        String equipmentReference = details.equipmentCode() == null
                ? details.equipmentId().toString()
                : details.equipmentCode();
        return "Maintenance " + maintenanceReference + " " + action
                + " for equipment " + equipmentReference + ".";
    }

    private static Set<UUID> recipientSet(UUID recipientUserId) {
        return recipientUserId == null
                ? Set.of()
                : Set.of(recipientUserId);
    }
}
