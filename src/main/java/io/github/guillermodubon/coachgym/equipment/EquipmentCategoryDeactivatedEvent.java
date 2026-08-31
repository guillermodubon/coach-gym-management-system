package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an equipment category has been deactivated successfully.
 */
public record EquipmentCategoryDeactivatedEvent(
        UUID categoryId,
        String categoryName,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public EquipmentCategoryDeactivatedEvent {
        categoryId =
                requireValue(
                        categoryId,
                        "Category identifier must be provided.");

        categoryName =
                requireText(
                        categoryName,
                        "Category name must be provided.");

        actorUserId =
                requireValue(
                        actorUserId,
                        "Actor user identifier must be provided.");

        actorIdentifier =
                requireText(
                        actorIdentifier,
                        "Actor identifier must be provided.");

        occurredAt =
                requireValue(
                        occurredAt,
                        "Event timestamp must be provided.");
    }

    private static <T> T requireValue(
            T value,
            String message) {

        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static String requireText(
            String value,
            String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }
}
