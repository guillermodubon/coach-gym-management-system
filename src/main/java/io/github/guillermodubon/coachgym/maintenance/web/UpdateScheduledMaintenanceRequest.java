package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.command.UpdateScheduledMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request for updating mutable fields of a scheduled maintenance work order.")
public record UpdateScheduledMaintenanceRequest(
        @NotNull LocalDate scheduledOn,
        @Size(max = 160) String providerName,
        @Size(max = 160) String technicianName,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal estimatedCost,
        @NotNull @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
        @Size(max = 2000) String notes,
        UUID assignedToUserId,
        @PositiveOrZero @Schema(minimum = "0") long version) {

    UpdateScheduledMaintenanceCommand toCommand(UUID maintenanceId) {
        return new UpdateScheduledMaintenanceCommand(
                maintenanceId, scheduledOn, providerName, technicianName,
                estimatedCost, currency, notes, assignedToUserId, version);
    }
}
