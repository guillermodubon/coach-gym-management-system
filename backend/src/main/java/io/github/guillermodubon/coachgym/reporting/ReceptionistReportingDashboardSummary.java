package io.github.guillermodubon.coachgym.reporting;

import java.util.Objects;

/** Minimal dashboard response with only the two ADR-approved receptionist sections. */
public record ReceptionistReportingDashboardSummary(
        ReportingContext context,
        ReceptionistMembershipSummary memberships,
        ReceptionistAccessSummary access)
        implements ReportingDashboardSummary {

    public ReceptionistReportingDashboardSummary {
        Objects.requireNonNull(context, "Reporting context is required.");
        Objects.requireNonNull(memberships, "Membership summary is required.");
        Objects.requireNonNull(access, "Access summary is required.");
    }
}
