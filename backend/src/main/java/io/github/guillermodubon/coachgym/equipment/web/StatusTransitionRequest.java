package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.MarkAvailableCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkOutOfServiceCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RetireEquipmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(
        description = """
                Request for a catalog-managed equipment status transition.

                Valid transitions:
                AVAILABLE to OUT_OF_SERVICE,
                OUT_OF_SERVICE to AVAILABLE,
                AVAILABLE to RETIRED,
                OUT_OF_SERVICE to RETIRED.

                RETIRED is terminal. Transitions involving MAINTENANCE are
                reserved for the maintenance module.
                """)
public record StatusTransitionRequest(

        @NotBlank
        @Size(max = 2000)
        @Schema(
                description = """
                        Required business reason stored in the append-only
                        status history.
                        """,
                example = "Routine safety inspection",
                maxLength = 2000,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String reason,

        @NotNull
        @PositiveOrZero
        @Schema(
                description = """
                        Current equipment version used for optimistic locking.
                        A stale version produces HTTP 409.
                        """,
                example = "0",
                minimum = "0",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        Long version) {

    MarkOutOfServiceCommand toOutOfServiceCommand(
            UUID equipmentId) {

        return new MarkOutOfServiceCommand(
                equipmentId,
                reason,
                version);
    }

    MarkAvailableCommand toAvailableCommand(
            UUID equipmentId) {

        return new MarkAvailableCommand(
                equipmentId,
                reason,
                version);
    }

    RetireEquipmentCommand toRetireCommand(
            UUID equipmentId) {

        return new RetireEquipmentCommand(
                equipmentId,
                reason,
                version);
    }
}