package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(
        description = """
                Request for administratively updating equipment.

                Only the documented fields are mutable. Identifier, equipment
                number, equipment code, lifecycle status, retirement fields,
                audit fields and timestamps are controlled by the server and
                are not accepted.

                Version is required for optimistic locking.
                """)
public record UpdateEquipmentRequest(

        @NotNull
        @Schema(
                description = "Active equipment-category identifier.",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        UUID categoryId,

        @NotBlank
        @Size(max = 160)
        @Schema(
                description = "Updated equipment name.",
                maxLength = 160,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 120)
        @Schema(maxLength = 120)
        String manufacturer,

        @Size(max = 120)
        @Schema(maxLength = 120)
        String model,

        @Size(max = 120)
        @Schema(maxLength = 120)
        String serialNumber,

        @Size(max = 160)
        @Schema(maxLength = 160)
        String location,

        String notes,

        LocalDate purchasedOn,

        @NotNull
        @PositiveOrZero
        @Schema(
                description = """
                        Current equipment version. A stale version produces
                        HTTP 409 with code EQUIPMENT_VERSION_CONFLICT.
                        """,
                example = "0",
                minimum = "0",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        Long version) {

    UpdateEquipmentCommand toCommand(
            UUID equipmentId) {

        EquipmentDefinition definition =
                EquipmentDefinition.create(
                        categoryId,
                        name,
                        manufacturer,
                        model,
                        serialNumber,
                        location,
                        notes,
                        purchasedOn);

        return new UpdateEquipmentCommand(
                equipmentId,
                definition,
                version);
    }
}