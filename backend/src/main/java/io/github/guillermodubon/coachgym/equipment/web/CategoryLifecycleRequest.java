package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.ActivateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.DeactivateEquipmentCategoryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

@Schema(
        description = """
                Request for activating or deactivating an equipment category.
                Version is required for optimistic locking.
                """)
public record CategoryLifecycleRequest(

        @NotNull
        @PositiveOrZero
        @Schema(
                description = """
                        Current category version. A stale version produces
                        HTTP 409 with code
                        EQUIPMENT_CATEGORY_VERSION_CONFLICT.
                        """,
                example = "0",
                minimum = "0",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        Long version) {

    ActivateEquipmentCategoryCommand toActivateCommand(
            UUID categoryId) {

        return new ActivateEquipmentCategoryCommand(
                categoryId,
                version);
    }

    DeactivateEquipmentCategoryCommand toDeactivateCommand(
            UUID categoryId) {

        return new DeactivateEquipmentCategoryCommand(
                categoryId,
                version);
    }
}
