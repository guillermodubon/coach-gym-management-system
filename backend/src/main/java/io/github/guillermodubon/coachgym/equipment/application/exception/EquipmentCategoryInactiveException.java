package io.github.guillermodubon.coachgym.equipment.application.exception;

import java.util.UUID;

/**
 * Thrown when an inactive category is assigned to new equipment or an equipment update
 * attempts to reassign to an inactive category. Maps to HTTP 409.
 */
public class EquipmentCategoryInactiveException extends RuntimeException {

    private final UUID categoryId;

    public EquipmentCategoryInactiveException(UUID categoryId) {
        super("Equipment category is inactive: " + categoryId);
        this.categoryId = categoryId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }
}
