package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import java.time.Instant;
import java.util.UUID;

/** Response body for a single equipment category. */
public record EquipmentCategoryResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static EquipmentCategoryResponse from(EquipmentCategoryDetails details) {
        return new EquipmentCategoryResponse(
                details.id(),
                details.name(),
                details.description(),
                details.active(),
                details.createdAt(),
                details.updatedAt(),
                details.version());
    }
}
