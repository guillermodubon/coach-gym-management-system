package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceCancelledEvent(
        UUID maintenanceId,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        UUID incidentId,
        MaintenanceStatus previousStatus,
        MaintenanceStatus newStatus,
        EquipmentMaintenanceOutcome equipmentOutcome,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
