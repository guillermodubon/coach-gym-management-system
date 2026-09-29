package io.github.guillermodubon.coachgym.reporting;

import java.time.LocalDate;

/**
 * Bounded date-only dashboard request. A null selection asks the application
 * service to resolve organization scope for an organization administrator or
 * the authenticated user's active branch otherwise.
 */
public record ReportingDashboardRequest(
        BranchReportingSelection selection,
        LocalDate fromInclusive,
        LocalDate toExclusive) {

    public ReportingDashboardRequest {
        requireDateRange(fromInclusive, toExclusive);
    }

    static void requireDateRange(LocalDate fromInclusive, LocalDate toExclusive) {
        if (fromInclusive == null || toExclusive == null
                || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "Reporting dates must define a non-empty half-open interval.");
        }
    }
}
