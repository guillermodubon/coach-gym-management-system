package io.github.guillermodubon.coachgym.maintenance;

import java.util.Objects;
import java.util.UUID;

/** Branch-attributed maintenance aggregate without work-order details. */
public record MaintenanceBranchReportingSummary(
        UUID branchId,
        MaintenanceReportingSummary metrics) {

    public MaintenanceBranchReportingSummary {
        Objects.requireNonNull(branchId, "Maintenance reporting branch id is required.");
        Objects.requireNonNull(metrics, "Maintenance reporting metrics are required.");
    }
}
