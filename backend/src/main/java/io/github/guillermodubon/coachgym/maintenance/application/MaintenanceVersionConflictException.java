package io.github.guillermodubon.coachgym.maintenance.application;

import java.util.UUID;

public final class MaintenanceVersionConflictException extends RuntimeException {
    private final UUID maintenanceId;
    private final long expectedVersion;

    public MaintenanceVersionConflictException(
            UUID maintenanceId,
            long expectedVersion) {
        super("Maintenance version conflict for " + maintenanceId
                + " at expected version " + expectedVersion + ".");
        this.maintenanceId = maintenanceId;
        this.expectedVersion = expectedVersion;
    }

    public UUID maintenanceId() { return maintenanceId; }
    public long expectedVersion() { return expectedVersion; }
}
