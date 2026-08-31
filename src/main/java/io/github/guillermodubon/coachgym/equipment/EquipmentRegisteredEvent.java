package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an equipment item has been persisted successfully.
 *
 * @param equipmentId equipment identifier
 * @param equipmentCode generated equipment code
 * @param categoryId equipment category identifier
 * @param actorUserId authenticated actor identifier
 * @param actorIdentifier immutable actor identifier snapshot
 * @param occurredAt event occurrence timestamp
 */
public record EquipmentRegisteredEvent(
        UUID equipmentId,
        String equipmentCode,
        UUID categoryId,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt
) {
    public EquipmentRegisteredEvent {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment identifier must be provided.");
        }
        if (equipmentCode == null || equipmentCode.isBlank()) {
            throw new IllegalArgumentException("Equipment code must be provided.");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("Equipment category identifier must be provided.");
        }
        if (actorUserId == null) {
            throw new IllegalArgumentException("Actor user identifier must be provided.");
        }
        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException("Actor identifier must be provided.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Event timestamp must be provided.");
        }

        equipmentCode = equipmentCode.trim();
        actorIdentifier = actorIdentifier.trim();
    }
}
