package io.github.guillermodubon.coachgym.equipment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of a piece of equipment, exposed as the module's public contract.
 *
 * <p>Field coverage matches the {@code gym.equipment} schema exactly.
 * Fields {@code equipmentNumber} and {@code equipmentCode} are DB-generated and immutable.
 * Retirement fields are null unless {@code status} is {@link EquipmentStatus#RETIRED}.
 */
public record EquipmentDetails(
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
}
