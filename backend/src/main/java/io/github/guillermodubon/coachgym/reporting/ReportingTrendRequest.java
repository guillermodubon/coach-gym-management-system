package io.github.guillermodubon.coachgym.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** Explicit scope, date interval, and calendar granularity for a focused trend. */
public record ReportingTrendRequest(
        BranchReportingSelection selection,
        LocalDate fromInclusive,
        LocalDate toExclusive,
        ReportingGranularity granularity) {

    public ReportingTrendRequest {
        Objects.requireNonNull(selection, "A reporting trend selection is required.");
        Objects.requireNonNull(granularity, "A reporting trend granularity is required.");
        ReportingDashboardRequest.requireDateRange(fromInclusive, toExclusive);
    }
}
