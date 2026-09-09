package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.DeactivateClientCommand;
import io.github.guillermodubon.coachgym.client.application.ReactivateClientCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record ClientLifecycleRequest(
        @NotBlank @Size(max = 2000) String reason,
        @NotNull @PositiveOrZero
        @Schema(description = "Current client version used for optimistic locking.")
        Long version) {

    DeactivateClientCommand toDeactivateCommand() {
        return new DeactivateClientCommand(reason, version);
    }

    ReactivateClientCommand toReactivateCommand() {
        return new ReactivateClientCommand(reason, version);
    }
}
