package io.github.guillermodubon.coachgym.maintenance;

/**
 * Allowed operational outcome for equipment after an in-progress maintenance
 * work order is completed or cancelled.
 *
 * <p>Retirement is deliberately excluded because equipment retirement remains
 * an explicit administrative operation owned by the equipment module.
 */
public enum EquipmentMaintenanceOutcome {
    AVAILABLE,
    OUT_OF_SERVICE
}
