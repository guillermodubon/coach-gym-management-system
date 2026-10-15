package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of an equipment category, exposed as the module's public contract.
 *
 * <p>Field coverage matches the {@code gym.equipment_categories} schema exactly.
 * No actor columns exist on that table and are therefore absent here.
 */
public record EquipmentCategoryDetails(
        UUID id,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
