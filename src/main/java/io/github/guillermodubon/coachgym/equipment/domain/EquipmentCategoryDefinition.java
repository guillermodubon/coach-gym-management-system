package io.github.guillermodubon.coachgym.equipment.domain;

/**
 * Immutable, validated value object for creating or updating an equipment category.
 *
 * <p>Field constraints match the {@code gym.equipment_categories} schema:
 * <ul>
 *   <li>{@code name}: VARCHAR(120) NOT NULL, btrim non-blank</li>
 *   <li>{@code description}: TEXT nullable</li>
 * </ul>
 */
public record EquipmentCategoryDefinition(String name, String description) {

    private static final int MAX_NAME_LENGTH = 120;

    public static EquipmentCategoryDefinition create(String name, String description) {
        String normalizedName = normalizeRequired(name, "Category name", MAX_NAME_LENGTH);
        String normalizedDescription = normalizeOptional(description);
        return new EquipmentCategoryDefinition(normalizedName, normalizedDescription);
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

    private static String normalizeOptional(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
