package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import java.util.List;
import java.util.Objects;

/** Immutable page of incident search results. */
public record IncidentPage(
        List<IncidentDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public IncidentPage {
        Objects.requireNonNull(items, "Incident page items are required.");
        items = List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Incident page index cannot be negative.");
        }
        if (size < 1) {
            throw new IllegalArgumentException(
                    "Incident page size must be positive.");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "Incident total elements cannot be negative.");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException(
                    "Incident total pages cannot be negative.");
        }
    }
}
