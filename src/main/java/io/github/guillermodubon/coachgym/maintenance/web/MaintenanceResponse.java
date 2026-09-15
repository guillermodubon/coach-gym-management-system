package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceResponse(
        UUID id, long maintenanceNumber, String maintenanceCode,
        UUID equipmentId, String equipmentCode, String equipmentName,
        UUID incidentId, String incidentCode,
        MaintenanceType maintenanceType, MaintenanceStatus status,
        LocalDate scheduledOn, Instant startedAt, Instant completedAt,
        String providerName, String technicianName,
        BigDecimal estimatedCost, BigDecimal actualCost, String currency,
        String actionsTaken, String notes,
        UUID createdByUserId, UUID assignedToUserId, UUID completedByUserId,
        Instant createdAt, Instant updatedAt, long version) {

    static MaintenanceResponse from(MaintenanceDetails details) {
        return new MaintenanceResponse(
                details.id(), details.maintenanceNumber(), details.maintenanceCode(),
                details.equipmentId(), details.equipmentCode(), details.equipmentName(),
                details.incidentId(), details.incidentCode(),
                details.maintenanceType(), details.status(), details.scheduledOn(),
                details.startedAt(), details.completedAt(), details.providerName(),
                details.technicianName(), details.estimatedCost(), details.actualCost(),
                details.currency(), details.actionsTaken(), details.notes(),
                details.createdByUserId(), details.assignedToUserId(),
                details.completedByUserId(), details.createdAt(), details.updatedAt(),
                details.version());
    }
}
