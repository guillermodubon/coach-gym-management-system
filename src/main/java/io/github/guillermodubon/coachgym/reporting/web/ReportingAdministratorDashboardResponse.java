package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.AdministratorReportingMetrics;
import java.util.Objects;

/** Organization or authorized-branch administrator dashboard projection. */
public record ReportingAdministratorDashboardResponse(
        String audience,
        ReportingApiContextResponse context,
        AdministratorReportingMetrics metrics)
        implements ReportingDashboardResponse {

    public ReportingAdministratorDashboardResponse {
        audience = "ADMINISTRATOR";
        Objects.requireNonNull(context);
        Objects.requireNonNull(metrics);
    }
}
