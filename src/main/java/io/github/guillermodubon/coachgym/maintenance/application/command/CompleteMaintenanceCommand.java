package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Command for completing in-progress maintenance and selecting equipment outcome. */
public record CompleteMaintenanceCommand(
        UUID maintenanceId,
        Instant completedAt,
        String actionsTaken,
        BigDecimal actualCost,
        String currency,
        EquipmentMaintenanceOutcome equipmentOutcome,
        long maintenanceVersion,
        long equipmentVersion) {

    public CompleteMaintenanceCommand {
        if (maintenanceId == null) {
            throw new MaintenanceValidationException("Maintenance id is required.");
        }
        requireVersion(maintenanceVersion, "Maintenance");
        requireVersion(equipmentVersion, "Equipment");
        new MaintenanceCompletion(
                completedAt, actionsTaken, actualCost, currency, equipmentOutcome);
    }

    public MaintenanceCompletion completion() {
        return new MaintenanceCompletion(
                completedAt, actionsTaken, actualCost, currency, equipmentOutcome);
    }

    private static void requireVersion(long value, String label) {
        if (value < 0) {
            throw new MaintenanceValidationException(
                    label + " version must not be negative.");
        }
    }
}
