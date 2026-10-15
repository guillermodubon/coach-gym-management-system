package io.github.guillermodubon.coachgym.reporting;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/** Bounded calendar buckets used by reporting trends. */
public enum ReportingGranularity {
    DAILY(366),
    WEEKLY(54),
    MONTHLY(13);

    private final int maximumBuckets;

    ReportingGranularity(int maximumBuckets) {
        this.maximumBuckets = maximumBuckets;
    }

    public int maximumBuckets() {
        return maximumBuckets;
    }

    public long bucketCount(ReportingRange range) {
        if (range == null) {
            throw new IllegalArgumentException("Reporting range is required.");
        }
        LocalDate lastIncluded = range.toExclusive().minusDays(1);
        return switch (this) {
            case DAILY -> range.calendarDays();
            case WEEKLY -> {
                LocalDate firstWeek = range.fromInclusive()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate lastWeek = lastIncluded
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield java.time.temporal.ChronoUnit.WEEKS.between(firstWeek, lastWeek) + 1;
            }
            case MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(
                    YearMonth.from(range.fromInclusive()), YearMonth.from(lastIncluded)) + 1;
        };
    }
}
