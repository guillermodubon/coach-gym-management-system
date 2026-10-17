package io.github.guillermodubon.coachgym.reporting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded and deterministically ordered comparison of explicitly selected branches. */
public record BranchComparisonReport(
        ReportingContext context,
        List<BranchComparisonRow> branches) {

    public static final int MAXIMUM_BRANCHES = 20;

    public BranchComparisonReport {
        Objects.requireNonNull(context, "Reporting context is required.");
        Objects.requireNonNull(branches, "Branch comparison rows are required.");
        if (context.selection().scope() != ReportingScope.AUTHORIZED_BRANCH_SET
                || branches.size() < 2 || branches.size() > MAXIMUM_BRANCHES
                || branches.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Branch comparison rows are invalid.");
        }
        Set<UUID> ids = new HashSet<>();
        for (BranchComparisonRow branch : branches) {
            if (!ids.add(branch.branch().branchId())) {
                throw new IllegalArgumentException("Branch comparison rows must be unique.");
            }
        }
        if (!ids.equals(Set.copyOf(context.selection().branchIds()))) {
            throw new IllegalArgumentException(
                    "Branch comparison rows must match the applied branch selection.");
        }
        List<BranchComparisonRow> sorted = new ArrayList<>(branches);
        sorted.sort(Comparator.comparing((BranchComparisonRow row) ->
                        row.branch().branchCode())
                .thenComparing(row -> row.branch().branchId()));
        branches = List.copyOf(sorted);
    }
}
