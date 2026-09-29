package io.github.guillermodubon.coachgym.reporting;

/** Role-specific immutable dashboard projection. */
public sealed interface ReportingDashboardSummary
        permits AdministratorReportingDashboardSummary,
                ReceptionistReportingDashboardSummary {

    ReportingContext context();
}
