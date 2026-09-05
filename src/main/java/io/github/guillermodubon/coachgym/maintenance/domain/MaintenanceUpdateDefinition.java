package io.github.guillermodubon.coachgym.maintenance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Mutable scheduling data allowed while a work order remains SCHEDULED. */
public record MaintenanceUpdateDefinition(
        LocalDate scheduledOn,
        String providerName,
        String technicianName,
        BigDecimal estimatedCost,
        String currency,
        String notes,
        UUID assignedToUserId) {

    public MaintenanceUpdateDefinition {
        if (scheduledOn == null) {
            throw new MaintenanceValidationException("Scheduled date is required.");
        }
        providerName = MaintenanceDefinition.normalizeOptional(
                providerName, "Provider name", 160);
        technicianName = MaintenanceDefinition.normalizeOptional(
                technicianName, "Technician name", 160);
        estimatedCost = MaintenanceDefinition.normalizeMoney(
                estimatedCost, "Estimated cost");
        currency = MaintenanceDefinition.normalizeCurrency(currency);
        notes = MaintenanceDefinition.normalizeOptional(notes, "Notes", 2_000);
    }
}
