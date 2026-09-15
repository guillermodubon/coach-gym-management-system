package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

public final class EquipmentMaintenanceNotFoundException extends RuntimeException {
    private final UUID equipmentId;

    public EquipmentMaintenanceNotFoundException(UUID equipmentId) {
        super("Equipment not found for maintenance operation: " + equipmentId + ".");
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
