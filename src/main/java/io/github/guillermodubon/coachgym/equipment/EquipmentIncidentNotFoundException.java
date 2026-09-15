package io.github.guillermodubon.coachgym.equipment;

import java.util.UUID;

/**
 * Raised through the public incident-coordination boundary when equipment
 * cannot be found.
 */
public final class EquipmentIncidentNotFoundException
        extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentIncidentNotFoundException(
            UUID equipmentId) {

        super("Equipment not found: " + equipmentId + ".");
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
