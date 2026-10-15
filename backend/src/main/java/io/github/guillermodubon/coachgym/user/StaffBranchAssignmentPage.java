package io.github.guillermodubon.coachgym.user;

import java.util.List;
import java.util.Objects;

/** Immutable bounded page of branch assignment records. */
public record StaffBranchAssignmentPage(
        List<StaffBranchAssignmentDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static final int MAX_SIZE = 100;

    public StaffBranchAssignmentPage {
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
        if (page < 0 || size < 1 || size > MAX_SIZE || totalElements < 0 || totalPages < 0) {
            throw new StaffBranchAssignmentValidationException("invalid assignment page bounds");
        }
        if (items.size() > size) {
            throw new StaffBranchAssignmentValidationException("items exceed page size");
        }
    }
}
