package io.github.guillermodubon.coachgym.equipment.application.command;

import java.util.UUID;

/**
 * Explicit command to transition equipment to the terminal {@code RETIRED} status.
 *
 * <p>Only {@code AVAILABLE → RETIRED} and {@code OUT_OF_SERVICE → RETIRED} are
 * permitted by the domain policy. Authorization (ADMIN only) is enforced in the
 * application service via {@code @PreAuthorize}.
 *
 * <p>The {@code reason} is required; it will be stored in both the retirement columns
 * of {@code gym.equipment} and the {@code gym.equipment_status_history} row.
 */
public record RetireEquipmentCommand(UUID equipmentId, String reason, long version) {

    public RetireEquipmentCommand {
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
