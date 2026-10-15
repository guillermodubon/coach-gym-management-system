package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import java.util.List;
import java.util.Objects;

/** Safe daily access trend; no credential or personal data is present. */
public record AccessTrendReport(
        ReportingContext context,
        ReportingGranularity granularity,
        List<AccessDailyTrendPoint> points) {

    public AccessTrendReport {
        Objects.requireNonNull(context, "Reporting context is required.");
        Objects.requireNonNull(granularity, "Reporting granularity is required.");
        Objects.requireNonNull(points, "Access trend points are required.");
        if (granularity != ReportingGranularity.DAILY
                || points.size() > ReportingGranularity.DAILY.maximumBuckets()
                || points.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Access trend points are invalid or unbounded.");
        }
        points = points.stream()
                .sorted(java.util.Comparator.comparing(AccessDailyTrendPoint::day))
                .toList();
    }
}
