package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(
        name = "UpdateEquipmentCategoryRequest",
        description = """
                Request for updating mutable equipment-category fields.

                The identifier, active state and timestamps are controlled by
                the server. The version is required for optimistic locking.
                """)
public record UpdateEquipmentCategoryRequest(

        @NotBlank
        @Size(max = 120)
        @Schema(
                description = "Updated category name.",
                example = "Cardio Equipment",
                maxLength = 120,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "Updated optional description.",
                example = "Updated cardiovascular equipment catalog")
        String description,

        @NotNull
        @PositiveOrZero
        @Schema(
                description = """
                        Current resource version used for optimistic locking.
                        A stale version produces HTTP 409.
                        """,
                example = "0",
                minimum = "0",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        Long version) {

    UpdateEquipmentCategoryCommand toCommand(
            UUID categoryId) {

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        name,
                        description);

        return new UpdateEquipmentCategoryCommand(
                categoryId,
                definition,
                version);
    }
}