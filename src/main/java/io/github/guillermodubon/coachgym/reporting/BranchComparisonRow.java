package io.github.guillermodubon.coachgym.reporting;

import java.util.Objects;

/** One authorized branch and the same administrator metrics used by the dashboard. */
public record BranchComparisonRow(
        ReportingBranchIdentity branch,
        AdministratorReportingMetrics metrics) {

    public BranchComparisonRow {
        Objects.requireNonNull(branch, "Reporting branch identity is required.");
        Objects.requireNonNull(metrics, "Branch comparison metrics are required.");
    }
}
