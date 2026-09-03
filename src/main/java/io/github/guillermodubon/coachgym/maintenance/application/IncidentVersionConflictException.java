package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

/** Raised when an incident update uses a stale optimistic-lock version. */
public final class IncidentVersionConflictException extends RuntimeException {
    private final UUID incidentId;
    private final long expectedVersion;

    public IncidentVersionConflictException(
            UUID incidentId,
            long expectedVersion) {
        super("Incident version conflict for " + incidentId
                + " using version " + expectedVersion + ".");
        this.incidentId = incidentId;
        this.expectedVersion = expectedVersion;
    }

    public UUID incidentId() {
        return incidentId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
