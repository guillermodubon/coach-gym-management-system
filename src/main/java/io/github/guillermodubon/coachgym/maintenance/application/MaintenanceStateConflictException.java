package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceStateConflictException extends RuntimeException {
    private final UUID maintenanceId;

    public MaintenanceStateConflictException(UUID maintenanceId, String message) {
        super(message);
        this.maintenanceId = maintenanceId;
    }

    public UUID maintenanceId() { return maintenanceId; }
}
