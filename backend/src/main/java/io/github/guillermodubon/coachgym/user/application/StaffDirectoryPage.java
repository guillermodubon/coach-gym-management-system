package io.github.guillermodubon.coachgym.user.application;

import java.util.List;
import java.util.Objects;

/** Immutable bounded directory page. */
public record StaffDirectoryPage(
        List<StaffDirectoryEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public StaffDirectoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
        if (page < 0 || size < 1 || size > StaffDirectorySearchQuery.MAX_SIZE
                || totalElements < 0 || totalPages < 0 || items.size() > size) {
            throw new IllegalArgumentException("Invalid directory page bounds.");
        }
    }
}
