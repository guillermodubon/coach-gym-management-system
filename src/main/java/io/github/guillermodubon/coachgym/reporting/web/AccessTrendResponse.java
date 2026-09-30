package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import io.github.guillermodubon.coachgym.reporting.AccessTrendReport;
import io.github.guillermodubon.coachgym.reporting.ReportingGranularity;
import java.util.List;

/** Bounded daily access activity response without credentials or personal data. */
public record AccessTrendResponse(
        ReportingApiContextResponse context,
        ReportingGranularity granularity,
        List<AccessDailyTrendPoint> points) {

    public AccessTrendResponse {
        points = List.copyOf(points);
    }

    static AccessTrendResponse from(AccessTrendReport report) {
        return new AccessTrendResponse(
                ReportingApiContextResponse.from(report.context()),
                report.granularity(),
                report.points());
    }
}
