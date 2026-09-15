package io.github.guillermodubon.coachgym.maintenance;

import java.util.UUID;

/** Minimal public work-order projection used exclusively for notification routing. */
public record MaintenanceNotificationDetails(
        UUID maintenanceId,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        UUID createdByUserId,
        UUID assignedToUserId) {

    public MaintenanceNotificationDetails {
        if (maintenanceId == null) {
            throw new IllegalArgumentException("Maintenance id is required.");
        }
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment id is required.");
        }
        maintenanceCode = normalize(maintenanceCode);
        equipmentCode = normalize(equipmentCode);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
