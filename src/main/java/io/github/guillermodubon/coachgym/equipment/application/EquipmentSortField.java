package io.github.guillermodubon.coachgym.equipment.application;

/**
 * Allowlisted sort fields for equipment list queries.
 *
 * <p>Each value maps to an exact {@code gym.equipment} column. No caller-provided
 * column name is accepted; only these enum constants are valid.
 *
 * <ul>
 *   <li>{@link #NAME} → {@code name} (default primary sort)</li>
 *   <li>{@link #CREATED_AT} → {@code created_at}</li>
 *   <li>{@link #STATUS} → {@code status}</li>
 *   <li>{@link #ID} → {@code id} (stable secondary sort, always appended)</li>
 * </ul>
 */
public enum EquipmentSortField {
    NAME,
    CREATED_AT,
    STATUS,
    ID
}
