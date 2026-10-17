package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.ReceptionistAccessSummary;
import io.github.guillermodubon.coachgym.reporting.ReceptionistMembershipSummary;
import java.util.Objects;

/** Minimal dashboard shape approved for branch-scoped receptionists. */
public record ReportingReceptionistDashboardResponse(
        String audience,
        ReportingApiContextResponse context,
        ReceptionistMembershipSummary memberships,
        ReceptionistAccessSummary access)
        implements ReportingDashboardResponse {

    public ReportingReceptionistDashboardResponse {
        audience = "RECEPTIONIST";
        Objects.requireNonNull(context);
        Objects.requireNonNull(memberships);
        Objects.requireNonNull(access);
    }
}
