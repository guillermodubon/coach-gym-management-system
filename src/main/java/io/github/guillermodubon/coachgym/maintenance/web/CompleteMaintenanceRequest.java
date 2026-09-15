package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.application.command.CompleteMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Request for completing an in-progress maintenance work order.")
public record CompleteMaintenanceRequest(
        @NotNull Instant completedAt,
        @NotBlank @Size(max = 2000) String actionsTaken,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal actualCost,
        @NotNull @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
        @NotNull EquipmentMaintenanceOutcome equipmentOutcome,
        @PositiveOrZero @Schema(minimum = "0") long maintenanceVersion,
        @PositiveOrZero @Schema(minimum = "0") long equipmentVersion) {

    CompleteMaintenanceCommand toCommand(UUID maintenanceId) {
        return new CompleteMaintenanceCommand(
                maintenanceId, completedAt, actionsTaken, actualCost,
                currency, equipmentOutcome, maintenanceVersion,
                equipmentVersion);
    }
}
