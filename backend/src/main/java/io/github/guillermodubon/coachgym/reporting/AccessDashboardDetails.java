package io.github.guillermodubon.coachgym.reporting;

/** Access decisions recorded during the current operational day. */
public record AccessDashboardDetails(
        long allowedToday,
        long deniedToday) {

    public AccessDashboardDetails {
        DashboardMetricValidation.nonNegative(
                allowedToday, "Allowed access count");
        DashboardMetricValidation.nonNegative(
                deniedToday, "Denied access count");
    }
}
