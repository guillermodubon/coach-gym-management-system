package io.github.guillermodubon.coachgym.reporting;

/** Current unresolved incident indicators visible to ADMIN. */
public record IncidentDashboardDetails(
        long open,
        long inProgress,
        long criticalOpen) {

    public IncidentDashboardDetails {
        DashboardMetricValidation.nonNegative(open, "Open incident count");
        DashboardMetricValidation.nonNegative(
                inProgress, "In-progress incident count");
        DashboardMetricValidation.nonNegative(
                criticalOpen, "Critical unresolved incident count");
        if (criticalOpen > open + inProgress) {
            throw new IllegalArgumentException(
                    "Critical unresolved incidents must not exceed all unresolved incidents.");
        }
    }
}
