package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Command for scheduling a preventive or corrective maintenance work order. */
public record ScheduleMaintenanceCommand(
        UUID equipmentId,
        UUID incidentId,
        MaintenanceType maintenanceType,
        LocalDate scheduledOn,
        String providerName,
        String technicianName,
        BigDecimal estimatedCost,
        String currency,
        String notes,
        UUID assignedToUserId) {

    public ScheduleMaintenanceCommand {
        new MaintenanceDefinition(
                equipmentId, incidentId, maintenanceType, scheduledOn,
                providerName, technicianName, estimatedCost, currency,
                notes, assignedToUserId);
    }

    public MaintenanceDefinition definition() {
        return new MaintenanceDefinition(
                equipmentId, incidentId, maintenanceType, scheduledOn,
                providerName, technicianName, estimatedCost, currency,
                notes, assignedToUserId);
    }
}
