package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Response body for a single equipment item. */
public record EquipmentResponse(
        UUID id,
        long equipmentNumber,
        String equipmentCode,
        UUID categoryId,
        String categoryName,
        String name,
        String manufacturer,
        String model,
        String serialNumber,
        String location,
        EquipmentStatus status,
        LocalDate purchasedOn,
        String notes,
        Instant retiredAt,
        UUID retiredByUserId,
        String retirementReason,
        UUID createdByUserId,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static EquipmentResponse from(EquipmentDetails details) {
        return new EquipmentResponse(
                details.id(),
                details.equipmentNumber(),
                details.equipmentCode(),
                details.categoryId(),
                details.categoryName(),
                details.name(),
                details.manufacturer(),
                details.model(),
                details.serialNumber(),
                details.location(),
                details.status(),
                details.purchasedOn(),
                details.notes(),
                details.retiredAt(),
                details.retiredByUserId(),
                details.retirementReason(),
                details.createdByUserId(),
                details.updatedByUserId(),
                details.createdAt(),
                details.updatedAt(),
                details.version());
    }
}
