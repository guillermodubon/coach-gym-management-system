package io.github.guillermodubon.coachgym.maintenance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Current work-order status snapshot and branch-local overdue count. */
public record MaintenanceReportingSummary(
        long totalMaintenance,
        long overdueScheduledMaintenance,
        Map<MaintenanceStatus, Long> countsByStatus) {

    public MaintenanceReportingSummary {
        Objects.requireNonNull(countsByStatus, "Maintenance status counts are required.");
        EnumMap<MaintenanceStatus, Long> normalized = new EnumMap<>(MaintenanceStatus.class);
        for (MaintenanceStatus status : MaintenanceStatus.values()) {
            normalized.put(status, 0L);
        }
        countsByStatus.forEach((status, count) -> {
            if (status == null || count == null || count < 0) {
                throw new IllegalArgumentException("Maintenance status counts are invalid.");
            }
            normalized.put(status, count);
        });
        if (totalMaintenance < 0 || overdueScheduledMaintenance < 0
                || normalized.values().stream().mapToLong(Long::longValue).sum() != totalMaintenance
                || overdueScheduledMaintenance > normalized.get(MaintenanceStatus.SCHEDULED)) {
            throw new IllegalArgumentException("Maintenance status counts must reconcile.");
        }
        countsByStatus = Collections.unmodifiableMap(normalized);
    }

    public static MaintenanceReportingSummary empty() {
        return new MaintenanceReportingSummary(0, 0, Map.of());
    }
}
