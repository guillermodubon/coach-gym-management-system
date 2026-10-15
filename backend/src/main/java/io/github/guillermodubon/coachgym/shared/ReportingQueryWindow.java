package io.github.guillermodubon.coachgym.shared;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Bounded, date-based query window shared by source-owned report readers. */
public record ReportingQueryWindow(
        LocalDate fromInclusive,
        LocalDate toExclusive,
        ZoneId timezone) {

    public static final int MAXIMUM_DAYS = 366;

    public ReportingQueryWindow {
        Objects.requireNonNull(fromInclusive, "Report start date is required.");
        Objects.requireNonNull(toExclusive, "Report exclusive end date is required.");
        Objects.requireNonNull(timezone, "Report timezone is required.");
        long days = ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days < 1 || days > MAXIMUM_DAYS) {
            throw new IllegalArgumentException(
                    "Report window must contain between 1 and 366 calendar days.");
        }
    }

    public Instant fromInclusiveInstant() {
        return fromInclusive.atStartOfDay(timezone).toInstant();
    }

    public Instant toExclusiveInstant() {
        return toExclusive.atStartOfDay(timezone).toInstant();
    }
}
