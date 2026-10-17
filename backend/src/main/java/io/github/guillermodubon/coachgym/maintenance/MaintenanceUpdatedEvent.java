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
        Instant occurredAt,
        UUID branchId) {

    public MaintenanceUpdatedEvent(UUID maintenanceId, String maintenanceCode,
            UUID equipmentId, String equipmentCode, UUID incidentId,
            LocalDate scheduledOn, BigDecimal estimatedCost, String currency,
            UUID actorUserId, String actorIdentifier, Instant occurredAt) {
        this(maintenanceId, maintenanceCode, equipmentId, equipmentCode,
                incidentId, scheduledOn, estimatedCost, currency, actorUserId,
                actorIdentifier, occurredAt, null);
    }
}
