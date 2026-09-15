package io.github.guillermodubon.coachgym.maintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceUpdatedEvent(
        UUID maintenanceId,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        UUID incidentId,
        LocalDate scheduledOn,
        BigDecimal estimatedCost,
        String currency,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
