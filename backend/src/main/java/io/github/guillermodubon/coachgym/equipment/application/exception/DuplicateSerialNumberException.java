package io.github.guillermodubon.coachgym.equipment.application.exception;

/** Thrown when equipment with the same serial number (case-insensitive) already exists. Maps to HTTP 409. */
public class DuplicateSerialNumberException extends RuntimeException {

    private final String serialNumber;

    public DuplicateSerialNumberException(String serialNumber) {
        super("Equipment with serial number '" + serialNumber + "' already exists.");
        this.serialNumber = serialNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }
}
