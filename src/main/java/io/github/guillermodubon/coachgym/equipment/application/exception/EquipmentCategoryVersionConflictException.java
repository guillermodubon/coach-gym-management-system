package io.github.guillermodubon.coachgym.equipment.application.exception;

import java.util.UUID;

/**
 * Thrown when an optimistic-lock version mismatch is detected on a category update
 * or lifecycle change. Maps to HTTP 409.
 */
public class EquipmentCategoryVersionConflictException extends RuntimeException {

    private final UUID categoryId;

    public EquipmentCategoryVersionConflictException(UUID categoryId) {
        super("Equipment category was modified by another request: " + categoryId);
        this.categoryId = categoryId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }
}
