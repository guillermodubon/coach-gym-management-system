package io.github.guillermodubon.coachgym.equipment.application.exception;

import java.util.UUID;

/** Thrown when no equipment category exists for the requested ID. Maps to HTTP 404. */
public class EquipmentCategoryNotFoundException extends RuntimeException {

    private final UUID categoryId;

    public EquipmentCategoryNotFoundException(UUID categoryId) {
        super("Equipment category not found: " + categoryId);
        this.categoryId = categoryId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }
}
