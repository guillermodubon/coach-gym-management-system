package io.github.guillermodubon.coachgym.reporting;

/** Current non-retired equipment indicators visible to ADMIN. */
public record EquipmentDashboardDetails(
        long available,
        long inMaintenance,
        long outOfService) {

    public EquipmentDashboardDetails {
        DashboardMetricValidation.nonNegative(available, "Available equipment count");
        DashboardMetricValidation.nonNegative(
                inMaintenance, "Equipment in maintenance count");
        DashboardMetricValidation.nonNegative(
                outOfService, "Out-of-service equipment count");
    }
}
