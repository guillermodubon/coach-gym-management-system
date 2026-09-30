package io.github.guillermodubon.coachgym.reporting;

import java.util.Objects;

/** Complete administrator dashboard; never used for a receptionist response. */
public record AdministratorReportingDashboardSummary(
        ReportingContext context,
        AdministratorReportingMetrics metrics)
        implements ReportingDashboardSummary {

    public AdministratorReportingDashboardSummary {
        Objects.requireNonNull(context, "Reporting context is required.");
        Objects.requireNonNull(metrics, "Administrator dashboard metrics are required.");
    }
}
