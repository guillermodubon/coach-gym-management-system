package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

/** Raised when the selected equipment cannot accept a new incident. */
public final class IncidentEquipmentUnavailableException
        extends RuntimeException {

    private final UUID equipmentId;

    public IncidentEquipmentUnavailableException(
            UUID equipmentId,
            String message) {
        super(message);
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
