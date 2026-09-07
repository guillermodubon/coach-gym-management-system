package io.github.guillermodubon.coachgym.reporting;

/** Current maintenance work-order indicators visible to ADMIN. */
public record MaintenanceDashboardDetails(
        long scheduled,
        long inProgress,
        long overdue) {

    public MaintenanceDashboardDetails {
        DashboardMetricValidation.nonNegative(
                scheduled, "Scheduled maintenance count");
        DashboardMetricValidation.nonNegative(
                inProgress, "In-progress maintenance count");
        DashboardMetricValidation.nonNegative(overdue, "Overdue maintenance count");
        if (overdue > scheduled) {
            throw new IllegalArgumentException(
                    "Overdue maintenance count must not exceed scheduled maintenance count.");
        }
    }
}
