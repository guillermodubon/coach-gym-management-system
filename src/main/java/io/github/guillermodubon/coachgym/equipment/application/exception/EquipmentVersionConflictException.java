package io.github.guillermodubon.coachgym.equipment.application.exception;

import java.util.UUID;

/**
 * Thrown when an optimistic-lock version mismatch is detected on an equipment update
 * or status transition. Maps to HTTP 409.
 */
public class EquipmentVersionConflictException extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentVersionConflictException(UUID equipmentId) {
        super("Equipment was modified by another request: " + equipmentId);
        this.equipmentId = equipmentId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }
}
