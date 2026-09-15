package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

/**
 * Raised when an incident-driven equipment operation uses a stale version.
 */
public final class EquipmentIncidentVersionConflictException extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentIncidentVersionConflictException(UUID equipmentId) {
        super("Equipment version conflict for " + equipmentId + ".");
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}

