package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Validated immutable definition used to schedule a maintenance work order. */
public record MaintenanceDefinition(
        UUID equipmentId,
        UUID incidentId,
        MaintenanceType maintenanceType,
        LocalDate scheduledOn,
        String providerName,
        String technicianName,
        BigDecimal estimatedCost,
        String currency,
        String notes,
        UUID assignedToUserId) {

    private static final int MAX_PROVIDER_NAME = 160;
    private static final int MAX_TECHNICIAN_NAME = 160;
    private static final int MAX_TEXT = 2_000;

    public MaintenanceDefinition {
        if (equipmentId == null) {
            throw new MaintenanceValidationException("Equipment id is required.");
        }
        if (maintenanceType == null) {
            throw new MaintenanceValidationException("Maintenance type is required.");
        }
        if (scheduledOn == null) {
            throw new MaintenanceValidationException("Scheduled date is required.");
        }
        if (maintenanceType == MaintenanceType.PREVENTIVE && incidentId != null) {
            throw new MaintenanceValidationException(
                    "Preventive maintenance cannot be linked to an incident.");
        }

        providerName = normalizeOptional(providerName, "Provider name", MAX_PROVIDER_NAME);
        technicianName = normalizeOptional(
                technicianName, "Technician name", MAX_TECHNICIAN_NAME);
        estimatedCost = normalizeMoney(estimatedCost, "Estimated cost");
        currency = normalizeCurrency(currency);
        notes = normalizeOptional(notes, "Notes", MAX_TEXT);
    }

    static String normalizeOptional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new MaintenanceValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    static String normalizeRequired(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, field, maxLength);
        if (normalized == null) {
            throw new MaintenanceValidationException(field + " is required.");
        }
        return normalized;
    }

    static BigDecimal normalizeMoney(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new MaintenanceValidationException(field + " must not be negative.");
        }
        if (value.scale() > 2) {
            throw new MaintenanceValidationException(
                    field + " must not have more than 2 decimal places.");
        }
        if (value.precision() - value.scale() > 10) {
            throw new MaintenanceValidationException(
                    field + " exceeds the supported amount range.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException("Currency is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new MaintenanceValidationException(
                    "Currency must contain exactly 3 uppercase letters.");
        }
        return normalized;
    }
}
