package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceNotFoundException extends RuntimeException {
    private final UUID maintenanceId;

    public MaintenanceNotFoundException(UUID maintenanceId) {
        super("Maintenance work order not found: " + maintenanceId + ".");
        this.maintenanceId = maintenanceId;
    }

    public UUID maintenanceId() {
        return maintenanceId;
    }
}
