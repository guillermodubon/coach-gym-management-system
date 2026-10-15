package io.github.guillermodubon.coachgym.reporting;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Date-only reporting interval in an explicit business timezone.
 *
 * <p>The interval is half-open: {@code [fromInclusive, toExclusive)}. The
 * instant bounds are derived from local start-of-day so daylight-saving
 * transitions are handled by the timezone rules.</p>
 */
public record ReportingRange(
        LocalDate fromInclusive,
        LocalDate toExclusive,
        ZoneId timezone) {

    public ReportingRange {
        Objects.requireNonNull(fromInclusive, "Reporting start date is required.");
        Objects.requireNonNull(toExclusive, "Reporting exclusive end date is required.");
        Objects.requireNonNull(timezone, "Reporting timezone is required.");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "Reporting start date must be before the exclusive end date.");
        }
    }

    public static ReportingRange of(
            LocalDate fromInclusive,
            LocalDate toExclusive,
            String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("Reporting timezone is required.");
        }
        try {
            return new ReportingRange(fromInclusive, toExclusive, ZoneId.of(timezone.strip()));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Reporting timezone is invalid.", exception);
        }
    }

    public long calendarDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(fromInclusive, toExclusive);
    }

    public Instant fromInclusiveInstant() {
        return fromInclusive.atStartOfDay(timezone).toInstant();
    }

    public Instant toExclusiveInstant() {
        return toExclusive.atStartOfDay(timezone).toInstant();
    }
}
