package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import java.math.BigDecimal;
import java.time.Instant;

/** Validated data required to complete an in-progress maintenance order. */
public record MaintenanceCompletion(
        Instant completedAt,
        String actionsTaken,
        BigDecimal actualCost,
        String currency,
        EquipmentMaintenanceOutcome equipmentOutcome) {

    public MaintenanceCompletion {
        if (completedAt == null) {
            throw new MaintenanceValidationException("Completion timestamp is required.");
        }
        actionsTaken = MaintenanceDefinition.normalizeRequired(
                actionsTaken, "Actions taken", 2_000);
        actualCost = MaintenanceDefinition.normalizeMoney(actualCost, "Actual cost");
        currency = MaintenanceDefinition.normalizeCurrency(currency);
        if (equipmentOutcome == null) {
            throw new MaintenanceValidationException("Equipment outcome is required.");
        }
    }

    /** Validates chronology against the persisted work-order start timestamp. */
    public void validateAgainst(Instant startedAt) {
        if (startedAt == null) {
            throw new MaintenanceValidationException(
                    "Maintenance must be started before it can be completed.");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new MaintenanceValidationException(
                    "Completion timestamp must not be before the start timestamp.");
        }
    }

    /** Validates that completion uses the work order's established currency. */
    public void validateCurrency(String workOrderCurrency) {
        String normalized = MaintenanceDefinition.normalizeCurrency(workOrderCurrency);
        if (!currency.equals(normalized)) {
            throw new MaintenanceValidationException(
                    "Actual cost currency must match the work-order currency.");
        }
    }
}
