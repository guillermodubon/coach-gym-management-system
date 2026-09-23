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
        Instant occurredAt,
        UUID branchId) {

    public MaintenanceScheduledEvent(UUID maintenanceId, String maintenanceCode,
            UUID equipmentId, String equipmentCode, UUID incidentId,
            MaintenanceType maintenanceType, LocalDate scheduledOn,
            BigDecimal estimatedCost, String currency, UUID actorUserId,
            String actorIdentifier, Instant occurredAt) {
        this(maintenanceId, maintenanceCode, equipmentId, equipmentCode,
                incidentId, maintenanceType, scheduledOn, estimatedCost, currency,
                actorUserId, actorIdentifier, occurredAt, null);
    }
}
