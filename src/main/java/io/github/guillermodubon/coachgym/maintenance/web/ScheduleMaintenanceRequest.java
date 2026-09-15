package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.command.ScheduleMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request for scheduling a preventive or corrective maintenance work order.")
public record ScheduleMaintenanceRequest(
        @NotNull UUID equipmentId,
        UUID incidentId,
        @NotNull MaintenanceType maintenanceType,
        @NotNull LocalDate scheduledOn,
        @Size(max = 160) String providerName,
        @Size(max = 160) String technicianName,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal estimatedCost,
        @NotNull @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
        @Size(max = 2000) String notes,
        UUID assignedToUserId) {

    ScheduleMaintenanceCommand toCommand() {
        return new ScheduleMaintenanceCommand(
                equipmentId, incidentId, maintenanceType, scheduledOn,
                providerName, technicianName, estimatedCost, currency,
                notes, assignedToUserId);
    }
}
