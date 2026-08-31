package io.github.guillermodubon.coachgym.equipment.application.command;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;

/**
 * Command to register a new piece of equipment.
 *
 * <p>Input strings are validated and normalised by
 * {@link EquipmentDefinition#create} before this command is constructed.
 * The application service resolves and validates the category before persistence.
 * The initial status ({@code AVAILABLE}) is set exclusively by the service.
 */
public record RegisterEquipmentCommand(EquipmentDefinition definition) {

    public RegisterEquipmentCommand {
        if (definition == null) {
            throw new IllegalArgumentException("Equipment definition must not be null.");
        }
    }
}
