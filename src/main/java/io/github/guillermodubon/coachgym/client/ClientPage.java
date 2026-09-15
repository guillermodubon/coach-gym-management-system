package io.github.guillermodubon.coachgym.client;

import java.util.List;

/** Immutable page of operational client summaries. */
public record ClientPage(
        List<ClientSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ClientPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("Client page must not be negative.");
        }
        if (size < 1 || size > ClientSearchQuery.MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Client page size must be between 1 and 100.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException(
                    "Client page totals must not be negative.");
        }
        if (items.size() > size) {
            throw new IllegalArgumentException(
                    "Client page items must not exceed the requested size.");
        }
    }
}
