package io.github.guillermodubon.coachgym.equipment.application.command;

import java.util.UUID;

/**
 * Command to activate an equipment category that is currently inactive.
 *
 * <p>Supported because V8 carries {@code is_active BOOLEAN NOT NULL DEFAULT TRUE}
 * and {@code version BIGINT NOT NULL DEFAULT 0} on {@code gym.equipment_categories}.
 */
public record ActivateEquipmentCategoryCommand(UUID categoryId, long version) {

    public ActivateEquipmentCategoryCommand {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID must not be null.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative.");
        }
    }
}
