package io.github.guillermodubon.coachgym.maintenance;

/**
 * Business classification of a maintenance work order.
 *
 * <p>These values match the {@code gym.maintenances.maintenance_type}
 * database constraint and form part of the maintenance module's public API.
 */
public enum MaintenanceType {
    PREVENTIVE,
    CORRECTIVE
}
