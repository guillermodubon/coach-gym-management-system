package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceEquipmentUnavailableException extends RuntimeException {
    private final UUID equipmentId;

    public MaintenanceEquipmentUnavailableException(UUID equipmentId, String message) {
        super(message);
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() { return equipmentId; }
}
