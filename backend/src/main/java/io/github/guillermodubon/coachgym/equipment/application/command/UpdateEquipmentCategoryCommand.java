package io.github.guillermodubon.coachgym.equipment.application.command;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import java.util.UUID;

/**
 * Command to update the mutable fields of an existing equipment category.
 *
 * <p>{@code version} is the caller-supplied optimistic-lock value and must be
 * non-negative. The application service passes it to the persistence layer to detect
 * concurrent modifications.
 */
public record UpdateEquipmentCategoryCommand(
        UUID categoryId,
        EquipmentCategoryDefinition definition,
        long version) {

    public UpdateEquipmentCategoryCommand {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID must not be null.");
        }
        if (definition == null) {
            throw new IllegalArgumentException("Category definition must not be null.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative.");
        }
    }
}
