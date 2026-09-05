package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.command.StartMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Request for starting a scheduled maintenance work order.")
public record StartMaintenanceRequest(
        @NotNull Instant startedAt,
        @NotBlank @Size(max = 2000) String reason,
        @PositiveOrZero @Schema(minimum = "0") long maintenanceVersion,
        @PositiveOrZero @Schema(minimum = "0") long equipmentVersion) {

    StartMaintenanceCommand toCommand(UUID maintenanceId) {
        return new StartMaintenanceCommand(
                maintenanceId, startedAt, reason,
                maintenanceVersion, equipmentVersion);
    }
}
