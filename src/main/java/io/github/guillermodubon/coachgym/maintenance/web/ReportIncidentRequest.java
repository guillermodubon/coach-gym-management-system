package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.application.command.ReportIncidentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(
        description =
                "Request for reporting an equipment incident.")
public record ReportIncidentRequest(

        @NotNull
        @Schema(
                description =
                        "Identifier of the equipment associated "
                                + "with the incident.",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        UUID equipmentId,

        @NotNull
        @Schema(
                description =
                        "Operational severity assigned to the "
                                + "incident.",
                allowableValues = {
                        "LOW",
                        "MEDIUM",
                        "HIGH",
                        "CRITICAL"
                },
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        IncidentPriority priority,

        @NotBlank
        @Size(max = 2_000)
        @Schema(
                description =
                        "Description of the equipment failure "
                                + "or operational issue.",
                example =
                        "The treadmill stops unexpectedly "
                                + "during operation.",
                maxLength = 2_000,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(
                description =
                        "Whether the equipment must be taken "
                                + "out of service as part of the "
                                + "same transaction.",
                defaultValue = "false")
        boolean takeOutOfService,

        @PositiveOrZero
        @Schema(
                description =
                        "Current optimistic-lock version of the "
                                + "equipment. Required when "
                                + "takeOutOfService is true.",
                example = "0",
                minimum = "0",
                nullable = true)
        Long equipmentVersion) {

    ReportIncidentCommand toCommand() {
        return new ReportIncidentCommand(
                equipmentId,
                priority,
                description,
                takeOutOfService,
                equipmentVersion);
    }
}