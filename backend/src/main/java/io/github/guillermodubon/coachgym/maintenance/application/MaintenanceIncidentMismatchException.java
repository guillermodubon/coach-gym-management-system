package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceIncidentMismatchException extends RuntimeException {
    private final UUID incidentId;
    private final UUID equipmentId;

    public MaintenanceIncidentMismatchException(UUID incidentId, UUID equipmentId) {
        super("Incident " + incidentId
                + " does not belong to equipment " + equipmentId + ".");
        this.incidentId = incidentId;
        this.equipmentId = equipmentId;
    }

    public UUID incidentId() { return incidentId; }
    public UUID equipmentId() { return equipmentId; }
}
