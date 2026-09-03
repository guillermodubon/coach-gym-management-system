package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

/**
 * Raised when equipment cannot satisfy an incident-driven operation because
 * of its current state.
 */
public final class EquipmentIncidentStateConflictException extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentIncidentStateConflictException(UUID equipmentId, String message) {
        super(message);
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}

