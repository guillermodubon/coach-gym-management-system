package io.github.guillermodubon.coachgym.maintenance;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import java.time.LocalDate;
import java.util.List;

/** Source-owned, read-only aggregates over canonical maintenance rows. */
public interface MaintenanceReportingQuery {

    /**
     * Counts scheduled work as overdue only when its date is before the
     * caller-resolved operational date in the ADR-approved reporting timezone.
     */
    MaintenanceReportingSummary summarize(ReportingQueryScope scope, LocalDate operationalDate);

    List<MaintenanceBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            LocalDate operationalDate);
}
