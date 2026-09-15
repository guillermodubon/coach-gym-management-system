package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Command for updating fields that remain mutable while an order is scheduled. */
public record UpdateScheduledMaintenanceCommand(
        UUID maintenanceId,
        LocalDate scheduledOn,
        String providerName,
        String technicianName,
        BigDecimal estimatedCost,
        String currency,
        String notes,
        UUID assignedToUserId,
        long version) {

    public UpdateScheduledMaintenanceCommand {
        requireId(maintenanceId);
        requireVersion(version);
        new MaintenanceUpdateDefinition(
                scheduledOn, providerName, technicianName, estimatedCost,
                currency, notes, assignedToUserId);
    }

    public MaintenanceUpdateDefinition definition() {
        return new MaintenanceUpdateDefinition(
                scheduledOn, providerName, technicianName, estimatedCost,
                currency, notes, assignedToUserId);
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new MaintenanceValidationException("Maintenance id is required.");
        }
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw new MaintenanceValidationException(
                    "Maintenance version must not be negative.");
        }
    }
}
