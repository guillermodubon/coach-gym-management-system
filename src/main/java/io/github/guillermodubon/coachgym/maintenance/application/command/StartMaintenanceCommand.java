package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.time.Instant;
import java.util.UUID;

/** Command for starting scheduled maintenance and placing equipment in maintenance. */
public record StartMaintenanceCommand(
        UUID maintenanceId,
        Instant startedAt,
        String reason,
        long maintenanceVersion,
        long equipmentVersion) {

    public StartMaintenanceCommand {
        if (maintenanceId == null) {
            throw new MaintenanceValidationException("Maintenance id is required.");
        }
        if (startedAt == null) {
            throw new MaintenanceValidationException("Start timestamp is required.");
        }
        reason = requireReason(reason);
        requireVersion(maintenanceVersion, "Maintenance");
        requireVersion(equipmentVersion, "Equipment");
    }

    private static String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException("Reason is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 2_000) {
            throw new MaintenanceValidationException(
                    "Reason must not exceed 2000 characters.");
        }
        return normalized;
    }

    private static void requireVersion(long value, String label) {
        if (value < 0) {
            throw new MaintenanceValidationException(
                    label + " version must not be negative.");
        }
    }
}
