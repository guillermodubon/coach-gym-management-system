package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.time.Instant;
import java.util.UUID;

/** Validated, allowlisted incident search criteria. */
public record IncidentSearchQuery(
        UUID equipmentId,
        IncidentStatus status,
        IncidentPriority priority,
        Instant reportedFrom,
        Instant reportedUntil,
        UUID reportedByUserId,
        UUID resolvedByUserId,
        String search,
        int page,
        int size,
        IncidentSortField sortField,
        IncidentSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public IncidentSearchQuery {
        if (page < 0) {
            throw new IncidentValidationException(
                    "Incident page index cannot be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IncidentValidationException(
                    "Incident page size must be between 1 and 100.");
        }
        if (reportedFrom != null
                && reportedUntil != null
                && reportedFrom.isAfter(reportedUntil)) {
            throw new IncidentValidationException(
                    "Incident reported-from timestamp cannot be after reported-until timestamp.");
        }
        search = normalizeOptional(search);
        sortField = sortField == null
                ? IncidentSortField.REPORTED_AT
                : sortField;
        direction = direction == null
                ? IncidentSortDirection.DESC
                : direction;
    }

    public static IncidentSearchQuery defaults() {
        return new IncidentSearchQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                DEFAULT_PAGE,
                DEFAULT_SIZE,
                IncidentSortField.REPORTED_AT,
                IncidentSortDirection.DESC);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
