package io.github.guillermodubon.coachgym.maintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceScheduledEvent(
        UUID maintenanceId,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        UUID incidentId,
        MaintenanceType maintenanceType,
        LocalDate scheduledOn,
        BigDecimal estimatedCost,
        String currency,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
