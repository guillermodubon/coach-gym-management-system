package io.github.guillermodubon.coachgym.equipment.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable, validated value object for registering or administratively updating equipment.
 *
 * <p>Field constraints match the {@code gym.equipment} schema:
 * <ul>
 *   <li>{@code categoryId}: NOT NULL foreign key</li>
 *   <li>{@code name}: VARCHAR(160) NOT NULL, btrim non-blank</li>
 *   <li>{@code manufacturer}: VARCHAR(120) nullable</li>
 *   <li>{@code model}: VARCHAR(120) nullable</li>
 *   <li>{@code serialNumber}: VARCHAR(120) nullable; case-insensitive unique when non-null</li>
 *   <li>{@code location}: VARCHAR(160) nullable</li>
 *   <li>{@code notes}: TEXT nullable</li>
 *   <li>{@code purchasedOn}: DATE nullable ({@link LocalDate})</li>
 * </ul>
 *
 * <p>No monetary field exists on the equipment table; do not add one.
 */
public record EquipmentDefinition(
        UUID categoryId,
        String name,
        String manufacturer,
        String model,
        String serialNumber,
        String location,
        String notes,
        LocalDate purchasedOn) {

    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_MANUFACTURER_LENGTH = 120;
    private static final int MAX_MODEL_LENGTH = 120;
    private static final int MAX_SERIAL_NUMBER_LENGTH = 120;
    private static final int MAX_LOCATION_LENGTH = 160;

    public static EquipmentDefinition create(
            UUID categoryId,
            String name,
            String manufacturer,
            String model,
            String serialNumber,
            String location,
            String notes,
            LocalDate purchasedOn) {
        if (categoryId == null) {
            throw new EquipmentValidationException("Equipment category must not be null.");
        }
        String normalizedName = normalizeRequired(name, "Equipment name", MAX_NAME_LENGTH);
        String normalizedManufacturer = normalizeOptional(manufacturer, "Manufacturer", MAX_MANUFACTURER_LENGTH);
        String normalizedModel = normalizeOptional(model, "Model", MAX_MODEL_LENGTH);
        String normalizedSerial = normalizeOptional(serialNumber, "Serial number", MAX_SERIAL_NUMBER_LENGTH);
        String normalizedLocation = normalizeOptional(location, "Location", MAX_LOCATION_LENGTH);
        String normalizedNotes = normalizeOptionalUnbounded(notes);
        return new EquipmentDefinition(
                categoryId,
                normalizedName,
                normalizedManufacturer,
                normalizedModel,
                normalizedSerial,
                normalizedLocation,
                normalizedNotes,
                purchasedOn);
    }

    private static String normalizeRequired(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new EquipmentValidationException(field + " must not be blank.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new EquipmentValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new EquipmentValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return trimmed;
    }

    private static String normalizeOptionalUnbounded(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
