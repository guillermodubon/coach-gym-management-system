package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCancellation;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.util.UUID;

/** Command for cancelling scheduled or in-progress maintenance. */
public record CancelMaintenanceCommand(
        UUID maintenanceId,
        String reason,
        EquipmentMaintenanceOutcome equipmentOutcome,
        long maintenanceVersion,
        Long equipmentVersion) {

    public CancelMaintenanceCommand {
        if (maintenanceId == null) {
            throw new MaintenanceValidationException("Maintenance id is required.");
        }
        if (maintenanceVersion < 0) {
            throw new MaintenanceValidationException(
                    "Maintenance version must not be negative.");
        }
        if (equipmentVersion != null && equipmentVersion < 0) {
            throw new MaintenanceValidationException(
                    "Equipment version must not be negative.");
        }
        if (equipmentOutcome != null && equipmentVersion == null) {
            throw new MaintenanceValidationException(
                    "Equipment version is required when an equipment outcome is supplied.");
        }
        new MaintenanceCancellation(reason, equipmentOutcome);
    }

    public MaintenanceCancellation cancellation() {
        return new MaintenanceCancellation(reason, equipmentOutcome);
    }
}
