package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;

/** Validated maintenance lifecycle transition. */
public record MaintenanceStatusTransition(
        MaintenanceStatus previousStatus,
        MaintenanceStatus resultingStatus,
        String reason) {

    public MaintenanceStatusTransition {
        if (previousStatus == null) {
            throw new MaintenanceValidationException("Previous status is required.");
        }
        if (resultingStatus == null) {
            throw new MaintenanceValidationException("Resulting status is required.");
        }
        if (previousStatus == resultingStatus) {
            throw new MaintenanceValidationException("Maintenance status must change.");
        }
        reason = MaintenanceDefinition.normalizeRequired(reason, "Reason", 2_000);
    }
}
