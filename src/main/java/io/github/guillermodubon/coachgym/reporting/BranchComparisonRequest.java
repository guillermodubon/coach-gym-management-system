package io.github.guillermodubon.coachgym.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** Explicit, bounded branch set and common reporting interval for comparison. */
public record BranchComparisonRequest(
        BranchReportingSelection selection,
        LocalDate fromInclusive,
        LocalDate toExclusive,
        boolean includeInactiveBranches) {

    public BranchComparisonRequest {
        Objects.requireNonNull(selection, "A branch comparison selection is required.");
        if (selection.scope() != ReportingScope.AUTHORIZED_BRANCH_SET) {
            throw new IllegalArgumentException(
                    "Branch comparison requires an explicit authorized branch set.");
        }
        ReportingDashboardRequest.requireDateRange(fromInclusive, toExclusive);
    }
}
