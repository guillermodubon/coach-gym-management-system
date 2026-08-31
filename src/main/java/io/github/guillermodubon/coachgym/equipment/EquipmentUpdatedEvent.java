package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an administrative update to a piece of equipment has been persisted.
 *
 * <p>Contains only the minimal audit-safe data needed to record the action.
 * Internal mutable state (field values before/after) is not included to keep
 * the event stable across future field additions.
 */
public record EquipmentUpdatedEvent(
        UUID equipmentId,
        String equipmentCode,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public EquipmentUpdatedEvent {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID must be provided.");
        }
        if (equipmentCode == null || equipmentCode.isBlank()) {
            throw new IllegalArgumentException("Equipment code must be provided.");
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
