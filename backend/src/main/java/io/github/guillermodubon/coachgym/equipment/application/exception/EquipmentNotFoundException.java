package io.github.guillermodubon.coachgym.equipment.application.exception;

import java.util.UUID;

/** Thrown when no equipment exists for the requested ID. Maps to HTTP 404. */
public class EquipmentNotFoundException extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentNotFoundException(UUID equipmentId) {
        super("Equipment not found: " + equipmentId);
        this.equipmentId = equipmentId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }
}
