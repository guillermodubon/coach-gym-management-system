package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceIncidentNotFoundException extends RuntimeException {
    private final UUID incidentId;

    public MaintenanceIncidentNotFoundException(UUID incidentId) {
        super("Incident not found for maintenance work order: " + incidentId + ".");
        this.incidentId = incidentId;
    }

    public UUID incidentId() { return incidentId; }
}
