package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceActiveOrderConflictException extends RuntimeException {
    private final UUID equipmentId;

    public MaintenanceActiveOrderConflictException(UUID equipmentId) {
        super("Equipment already has an in-progress maintenance work order: "
                + equipmentId + ".");
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() { return equipmentId; }
}
