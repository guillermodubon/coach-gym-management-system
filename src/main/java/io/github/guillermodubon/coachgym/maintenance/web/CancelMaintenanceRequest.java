package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.application.command.CancelMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Request for cancelling a scheduled or in-progress maintenance work order.")
public record CancelMaintenanceRequest(
        @NotBlank @Size(max = 2000) String reason,
        EquipmentMaintenanceOutcome equipmentOutcome,
        @PositiveOrZero @Schema(minimum = "0") long maintenanceVersion,
        @PositiveOrZero @Schema(minimum = "0", nullable = true) Long equipmentVersion) {

    CancelMaintenanceCommand toCommand(UUID maintenanceId) {
        return new CancelMaintenanceCommand(
                maintenanceId, reason, equipmentOutcome,
                maintenanceVersion, equipmentVersion);
    }
}
