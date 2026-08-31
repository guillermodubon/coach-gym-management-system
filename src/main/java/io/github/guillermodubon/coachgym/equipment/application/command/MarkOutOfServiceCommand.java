package io.github.guillermodubon.coachgym.equipment.application.command;

import java.util.UUID;

/**
 * Explicit command to transition equipment from {@code AVAILABLE} to
 * {@code OUT_OF_SERVICE}.
 *
 * <p>The status-transition policy is enforced in the domain layer via
 * {@link io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusPolicy}.
 * The {@code reason} is required and will be recorded in
 * {@code gym.equipment_status_history}.
 */
public record MarkOutOfServiceCommand(UUID equipmentId, String reason, long version) {

    public MarkOutOfServiceCommand {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID must not be null.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative.");
        }
    }
}
