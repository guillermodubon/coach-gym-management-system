package io.github.guillermodubon.coachgym.equipment.application.exception;

/** Thrown when a category with the same name (case-insensitive) already exists. Maps to HTTP 409. */
public class DuplicateEquipmentCategoryException extends RuntimeException {

    private final String name;

    public DuplicateEquipmentCategoryException(String name) {
        super("Equipment category with name '" + name + "' already exists.");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
