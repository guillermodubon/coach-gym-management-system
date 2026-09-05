package io.github.guillermodubon.coachgym.notification.application;

import java.util.UUID;

/** Raised when a maintenance lifecycle event cannot be enriched for routing. */
public class MaintenanceNotificationUnavailableException extends RuntimeException {

    private final UUID maintenanceId;

    public MaintenanceNotificationUnavailableException(UUID maintenanceId) {
        super("Maintenance work order could not be resolved for notification delivery.");
        this.maintenanceId = maintenanceId;
    }

    public UUID maintenanceId() {
        return maintenanceId;
    }
}
