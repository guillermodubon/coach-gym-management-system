package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

/** Raised when an incident does not exist. */
public final class IncidentNotFoundException extends RuntimeException {
    private final UUID incidentId;

    public IncidentNotFoundException(UUID incidentId) {
        super("Incident not found: " + incidentId + ".");
        this.incidentId = incidentId;
    }

    public UUID incidentId() {
        return incidentId;
    }
}
