package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a catalog-managed equipment status transition has been
 * persisted (equipment row updated AND history row inserted atomically).
 *
 * <p>Uses the root-package {@link EquipmentStatus} to avoid leaking the
 * domain package across module boundaries.
 */
public record EquipmentStatusChangedEvent(
        UUID equipmentId,
        String equipmentCode,
        EquipmentStatus previousStatus,
        EquipmentStatus newStatus,
        String reason,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public EquipmentStatusChangedEvent {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID must be provided.");
        }
        if (equipmentCode == null || equipmentCode.isBlank()) {
            throw new IllegalArgumentException("Equipment code must be provided.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must be provided.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must be provided.");
        }
        if (actorUserId == null) {
            throw new IllegalArgumentException("Actor user ID must be provided.");
        }
        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException("Actor identifier must be provided.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must be provided.");
        }
    }
}
