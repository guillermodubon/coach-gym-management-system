package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.domain.PlanValidationException;
import java.util.Locale;

public record PlanSearchQuery(
        Boolean active,
        String name,
        int page,
        int size,
        PlanSortField sortField,
        PlanSortDirection direction) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public PlanSearchQuery {
        if (page < 0) {
            throw new PlanValidationException("Page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new PlanValidationException("Page size must be between 1 and 100.");
        }
        if (sortField == null || direction == null) {
            throw new PlanValidationException("Sort field and direction must be provided.");
        }
        name = name == null || name.isBlank() ? "" : name.trim();
    }

    public static PlanSearchQuery from(
            Boolean active,
            String name,
            int page,
            int size,
            String sort,
            String direction) {
        return new PlanSearchQuery(
                active,
                name,
                page,
                size,
                parseSortField(sort),
                parseDirection(direction));
    }

    private static PlanSortField parseSortField(String value) {
        String normalized = value == null || value.isBlank() ? "NAME" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return PlanSortField.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new PlanValidationException("Unsupported plan sort field.");
        }
    }

    private static PlanSortDirection parseDirection(String value) {
        String normalized = value == null || value.isBlank() ? "ASC" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return PlanSortDirection.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new PlanValidationException("Unsupported plan sort direction.");
        }
    }
}
