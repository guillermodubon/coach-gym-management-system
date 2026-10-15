package io.github.guillermodubon.coachgym.equipment.domain;

/** Thrown when an equipment or category value fails a domain invariant. */
public class EquipmentValidationException extends RuntimeException {

    public EquipmentValidationException(String message) {
        super(message);
    }
}
