package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.CreateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
        name = "CreateEquipmentCategoryRequest",
        description = """
                Request for creating an equipment category.

                The identifier, active state, timestamps and version are
                controlled by the server and are not accepted.
                """)
public record EquipmentCategoryRequest(

        @NotBlank
        @Size(max = 120)
        @Schema(
                description = "Unique category name.",
                example = "Cardio",
                maxLength = 120,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "Optional category description.",
                example = "Cardiovascular training equipment")
        String description) {

    CreateEquipmentCategoryCommand toCreateCommand() {
        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        name,
                        description);

        return new CreateEquipmentCategoryCommand(
                definition);
    }
}