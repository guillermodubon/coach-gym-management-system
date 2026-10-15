package io.github.guillermodubon.coachgym.access;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Source-owned, read-only aggregates over immutable physical access attempts. */
public interface AccessReportingQuery {

    /**
     * Returns only the existing recipient-safe operational-day counts. This
     * projection intentionally omits source and denial-reason detail.
     */
    AccessOperationalDaySummary summarizeOperationalDay(
            ReportingQueryScope scope,
            LocalDate day,
            ZoneId timezone);

    /** Detailed source/reason aggregate for roles authorized for access detail reporting. */
    AccessReportingSummary summarize(ReportingQueryScope scope, ReportingQueryWindow window);

    /** Detailed branch breakdown; callers must authorize the resolved branch selection first. */
    List<AccessBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window);

    /** Detailed daily trend; callers must authorize the requested metric group first. */
    List<AccessDailyTrendPoint> dailyTrend(
            ReportingQueryScope scope,
            ReportingQueryWindow window);
}
