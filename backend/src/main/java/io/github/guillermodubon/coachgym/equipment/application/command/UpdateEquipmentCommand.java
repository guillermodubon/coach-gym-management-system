package io.github.guillermodubon.coachgym.equipment.application.command;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import java.util.UUID;

/**
 * Command to administratively update the mutable fields of an existing piece of equipment.
 *
 * <p>The update allowlist matches the schema decisions: {@code name},
 * {@code equipment_category_id}, {@code manufacturer}, {@code model},
 * {@code serial_number}, {@code location}, {@code notes}, {@code purchased_on}.
 *
 * <p>Immutable fields ({@code id}, {@code equipment_number}, {@code equipment_code},
 * {@code status}, {@code created_at}, {@code created_by_user_id}, retirement columns)
 * must never be included in the command and must never be altered by the service.
 *
 * <p>{@code version} is the caller-supplied optimistic-lock value and must be
 * non-negative.
 */
public record UpdateEquipmentCommand(
        UUID equipmentId,
        EquipmentDefinition definition,
        long version) {

    public UpdateEquipmentCommand {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID must not be null.");
        }
        if (definition == null) {
            throw new IllegalArgumentException("Equipment definition must not be null.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative.");
        }
    }
}
