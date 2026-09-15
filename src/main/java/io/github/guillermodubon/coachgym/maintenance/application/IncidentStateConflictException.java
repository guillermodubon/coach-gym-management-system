package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

/** Raised when an incident operation conflicts with its current state. */
public final class IncidentStateConflictException extends RuntimeException {
    private final UUID incidentId;

    public IncidentStateConflictException(
            UUID incidentId,
            String message) {
        super(message);
        this.incidentId = incidentId;
    }

    public UUID incidentId() {
        return incidentId;
    }
}
