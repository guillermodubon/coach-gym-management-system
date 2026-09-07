package io.github.guillermodubon.coachgym.reporting;

/** Unread notification indicator for the authenticated dashboard user. */
public record DashboardNotificationDetails(long unread) {

    public DashboardNotificationDetails {
        DashboardMetricValidation.nonNegative(
                unread, "Unread notification count");
    }
}
