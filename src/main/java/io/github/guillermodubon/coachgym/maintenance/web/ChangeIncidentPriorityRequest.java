package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.application.command.ChangeIncidentPriorityCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChangeIncidentPriorityRequest(
        @NotNull IncidentPriority priority,
        @NotBlank @Size(max = 2000) String reason,
        @NotNull @PositiveOrZero Long version) {

    ChangeIncidentPriorityCommand toCommand(UUID incidentId) {
        return new ChangeIncidentPriorityCommand(
                incidentId, priority, reason, version);
    }
}
