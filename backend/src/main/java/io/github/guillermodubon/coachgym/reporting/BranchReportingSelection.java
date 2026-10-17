package io.github.guillermodubon.coachgym.reporting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Normalized branch filter attached to a reporting request.
 *
 * <p>Branch identifiers are filters only. Callers must resolve organization
 * ownership, current branch status, and actor authority before querying data.</p>
 */
public record BranchReportingSelection(
        ReportingScope scope,
        List<UUID> branchIds) {

    public BranchReportingSelection {
        Objects.requireNonNull(scope, "Reporting scope is required.");
        Objects.requireNonNull(branchIds, "Branch identifiers are required.");
        if (branchIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Branch identifiers must not contain null.");
        }
        branchIds = List.copyOf(new TreeSet<>(branchIds));
        switch (scope) {
            case ORGANIZATION -> requireCount(branchIds, 0, 0);
            case SINGLE_BRANCH, ACTIVE_BRANCH -> requireCount(branchIds, 1, 1);
            case AUTHORIZED_BRANCH_SET -> requireCount(branchIds, 2, Integer.MAX_VALUE);
        }
    }

    public static BranchReportingSelection organizationWide() {
        return new BranchReportingSelection(ReportingScope.ORGANIZATION, List.of());
    }

    public static BranchReportingSelection singleBranch(UUID branchId) {
        return new BranchReportingSelection(ReportingScope.SINGLE_BRANCH, List.of(branchId));
    }

    public static BranchReportingSelection activeBranch(UUID branchId) {
        return new BranchReportingSelection(ReportingScope.ACTIVE_BRANCH, List.of(branchId));
    }

    public static BranchReportingSelection authorizedBranches(Collection<UUID> branchIds) {
        Objects.requireNonNull(branchIds, "Branch identifiers are required.");
        return new BranchReportingSelection(
                ReportingScope.AUTHORIZED_BRANCH_SET,
                List.copyOf(branchIds));
    }

    private static void requireCount(List<UUID> branchIds, int minimum, int maximum) {
        if (branchIds.size() < minimum || branchIds.size() > maximum) {
            throw new IllegalArgumentException("Branch count does not match reporting scope.");
        }
    }
}
