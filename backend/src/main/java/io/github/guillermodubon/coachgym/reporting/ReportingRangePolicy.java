package io.github.guillermodubon.coachgym.reporting;

/** Bounded range and bucket policy for reporting trends. */
public record ReportingRangePolicy(int maximumDays) {

    public static final int DEFAULT_MAXIMUM_DAYS = 366;

    public ReportingRangePolicy {
        if (maximumDays < 1 || maximumDays > DEFAULT_MAXIMUM_DAYS) {
            throw new IllegalArgumentException(
                    "Reporting maximum days must be between 1 and 366.");
        }
    }

    public static ReportingRangePolicy defaults() {
        return new ReportingRangePolicy(DEFAULT_MAXIMUM_DAYS);
    }

    public ReportingRange validate(ReportingRange range, ReportingGranularity granularity) {
        if (range == null || granularity == null) {
            throw new IllegalArgumentException("Reporting range and granularity are required.");
        }
        if (range.calendarDays() > maximumDays) {
            throw new IllegalArgumentException(
                    "Reporting range exceeds the configured maximum number of days.");
        }
        if (granularity.bucketCount(range) > granularity.maximumBuckets()) {
            throw new IllegalArgumentException(
                    "Reporting range exceeds the maximum number of trend buckets.");
        }
        return range;
    }
}
