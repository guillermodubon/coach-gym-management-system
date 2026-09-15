package io.github.guillermodubon.coachgym.maintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceCompletedEvent(
        UUID maintenanceId,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        UUID incidentId,
        MaintenanceStatus previousStatus,
        MaintenanceStatus newStatus,
        EquipmentMaintenanceOutcome equipmentOutcome,
        BigDecimal actualCost,
        String currency,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
