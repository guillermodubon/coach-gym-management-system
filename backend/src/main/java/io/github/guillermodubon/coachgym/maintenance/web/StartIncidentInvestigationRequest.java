package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.command.StartIncidentInvestigationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StartIncidentInvestigationRequest(
        @NotBlank @Size(max = 2000) String reason,
        @NotNull @PositiveOrZero Long version) {

    StartIncidentInvestigationCommand toCommand(UUID incidentId) {
        return new StartIncidentInvestigationCommand(
                incidentId, reason, version);
    }
}
