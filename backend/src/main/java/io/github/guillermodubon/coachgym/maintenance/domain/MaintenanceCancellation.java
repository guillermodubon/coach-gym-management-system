package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;

/** Validated cancellation data for a scheduled or in-progress work order. */
public record MaintenanceCancellation(
        String reason,
        EquipmentMaintenanceOutcome equipmentOutcome) {

    public MaintenanceCancellation {
        reason = MaintenanceDefinition.normalizeRequired(reason, "Reason", 2_000);
    }

    /**
     * Validates outcome semantics against the current status.
     * Scheduled work has not changed equipment state and therefore accepts no
     * outcome. In-progress work requires an explicit safe operational outcome.
     */
    public void validateFor(MaintenanceStatus currentStatus) {
        if (currentStatus == null) {
            throw new MaintenanceValidationException("Current status is required.");
        }
        switch (currentStatus) {
            case SCHEDULED -> {
                if (equipmentOutcome != null) {
                    throw new MaintenanceValidationException(
                            "Scheduled maintenance cancellation must not define an equipment outcome.");
                }
            }
            case IN_PROGRESS -> {
                if (equipmentOutcome == null) {
                    throw new MaintenanceValidationException(
                            "In-progress maintenance cancellation requires an equipment outcome.");
                }
            }
            case COMPLETED, CANCELLED -> throw new MaintenanceValidationException(
                    "Terminal maintenance cannot be cancelled.");
        }
    }
}
