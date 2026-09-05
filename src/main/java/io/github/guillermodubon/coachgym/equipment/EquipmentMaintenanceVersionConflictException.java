package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

public final class EquipmentMaintenanceVersionConflictException extends RuntimeException {
    private final UUID equipmentId;

    public EquipmentMaintenanceVersionConflictException(UUID equipmentId) {
        super("Equipment version conflict for maintenance operation: " + equipmentId + ".");
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
