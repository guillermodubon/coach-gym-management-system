package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

public final class EquipmentMaintenanceStateConflictException extends RuntimeException {
    private final UUID equipmentId;

    public EquipmentMaintenanceStateConflictException(UUID equipmentId, String message) {
        super(message);
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
