package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.command.ResolveIncidentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ResolveIncidentRequest(
        @NotBlank @Size(max = 2000) String resolutionNotes,
        @NotNull @PositiveOrZero Long version) {

    ResolveIncidentCommand toCommand(UUID incidentId) {
        return new ResolveIncidentCommand(
                incidentId, resolutionNotes, version);
    }
}
