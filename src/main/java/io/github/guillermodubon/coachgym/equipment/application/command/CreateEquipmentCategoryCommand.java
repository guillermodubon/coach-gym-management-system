package io.github.guillermodubon.coachgym.equipment.application.command;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;

/**
 * Command to create a new equipment category.
 *
 * <p>Input strings are validated and normalised by
 * {@link EquipmentCategoryDefinition#create(String, String)} before the command is
 * constructed. The {@code definition} field carries the validated value object.
 */
public record CreateEquipmentCategoryCommand(EquipmentCategoryDefinition definition) {

    public CreateEquipmentCategoryCommand {
        if (definition == null) {
            throw new IllegalArgumentException("Category definition must not be null.");
        }
    }
}
