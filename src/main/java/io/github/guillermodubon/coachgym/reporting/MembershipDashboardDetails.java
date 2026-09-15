package io.github.guillermodubon.coachgym.reporting;

/** Current membership indicators visible to ADMIN and RECEPTIONIST. */
public record MembershipDashboardDetails(
        long active,
        long frozen,
        long expiringSoon) {

    public MembershipDashboardDetails {
        DashboardMetricValidation.nonNegative(active, "Active membership count");
        DashboardMetricValidation.nonNegative(frozen, "Frozen membership count");
        DashboardMetricValidation.nonNegative(
                expiringSoon, "Expiring membership count");
    }
}
