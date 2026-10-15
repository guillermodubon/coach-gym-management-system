package io.github.guillermodubon.coachgym.equipment.application;

/**
 * Allowlisted sort fields for equipment category list queries.
 *
 * <ul>
 *   <li>{@link #NAME} → {@code name} (default)</li>
 *   <li>{@link #ID} → {@code id} (stable secondary sort, always appended)</li>
 * </ul>
 */
public enum EquipmentCategorySortField {
    NAME,
    ID
}
