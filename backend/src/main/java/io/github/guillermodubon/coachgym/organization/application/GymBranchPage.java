package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import java.util.List;

/** Immutable bounded page of canonical branch details. */
public record GymBranchPage(
        List<GymBranchDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public GymBranchPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("Branch page must not be negative.");
        }
        if (size < 1 || size > GymBranchSearchQuery.MAX_SIZE) {
            throw new IllegalArgumentException("Branch page size must be between 1 and 100.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Branch page totals must not be negative.");
        }
        if (items.size() > size) {
            throw new IllegalArgumentException("Branch page items must not exceed the requested size.");
        }
    }
}
