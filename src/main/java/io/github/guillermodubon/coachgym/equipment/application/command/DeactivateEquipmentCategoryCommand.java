package io.github.guillermodubon.coachgym.equipment.application.command;

import java.util.UUID;

/**
 * Command to deactivate an equipment category.
 *
 * <p>A deactivated category cannot be assigned to new equipment registrations.
 * Supported because V8 carries {@code is_active} and {@code version} on
 * {@code gym.equipment_categories}.
 */
public record DeactivateEquipmentCategoryCommand(UUID categoryId, long version) {

    public DeactivateEquipmentCategoryCommand {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID must not be null.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative.");
        }
    }
}
