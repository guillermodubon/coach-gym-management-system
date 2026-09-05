package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.time.LocalDate;
import java.util.UUID;

/** Validated and allowlisted query for the maintenance work-order catalog. */
public record MaintenanceSearchQuery(
        UUID equipmentId,
        UUID incidentId,
        MaintenanceType maintenanceType,
        MaintenanceStatus status,
        LocalDate scheduledFrom,
        LocalDate scheduledUntil,
        UUID createdByUserId,
        UUID assignedToUserId,
        String providerName,
        String search,
        int page,
        int size,
        MaintenanceSortField sortField,
        MaintenanceSortDirection direction) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public MaintenanceSearchQuery {
        if (page < 0) {
            throw new MaintenanceValidationException(
                    "Maintenance page index must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new MaintenanceValidationException(
                    "Maintenance page size must be between 1 and 100.");
        }
        if (scheduledFrom != null && scheduledUntil != null
                && scheduledFrom.isAfter(scheduledUntil)) {
            throw new MaintenanceValidationException(
                    "Scheduled-from date must not be after scheduled-until date.");
        }
        providerName = normalize(providerName, "Provider-name filter");
        search = normalize(search, "Maintenance search");
        sortField = sortField == null
                ? MaintenanceSortField.SCHEDULED_ON : sortField;
        direction = direction == null
                ? MaintenanceSortDirection.ASC : direction;
    }

    public static MaintenanceSearchQuery defaults() {
        return new MaintenanceSearchQuery(
                null, null, null, null, null, null,
                null, null, null, null, 0, DEFAULT_SIZE,
                MaintenanceSortField.SCHEDULED_ON,
                MaintenanceSortDirection.ASC);
    }

    public static MaintenanceSearchQuery from(
            UUID equipmentId,
            UUID incidentId,
            MaintenanceType maintenanceType,
            MaintenanceStatus status,
            LocalDate scheduledFrom,
            LocalDate scheduledUntil,
            UUID createdByUserId,
            UUID assignedToUserId,
            String providerName,
            String search,
            int page,
            int size,
            String sort,
            String direction) {
        return new MaintenanceSearchQuery(
                equipmentId, incidentId, maintenanceType, status,
                scheduledFrom, scheduledUntil, createdByUserId,
                assignedToUserId, providerName, search, page, size,
                MaintenanceSortField.from(sort),
                MaintenanceSortDirection.from(direction));
    }

    private static String normalize(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw new MaintenanceValidationException(
                    label + " must not exceed 160 characters.");
        }
        return normalized;
    }
}
