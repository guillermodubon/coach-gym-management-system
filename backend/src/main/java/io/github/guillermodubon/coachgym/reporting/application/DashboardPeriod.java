package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fully resolved operational period used by reporting query adapters.
 *
 * <p>Date boundaries are inclusive for business dates. Timestamp boundaries
 * use the half-open interval [fromInclusive, untilExclusive).</p>
 */
public record DashboardPeriod(
        DashboardPeriodDetails dates,
        LocalDate operationalDate,
        ZoneId zoneId,
        Instant fromInclusive,
        Instant untilExclusive) {

    public DashboardPeriod {
        if (dates == null) {
            throw new ReportingValidationException(
                    "Resolved dashboard dates are required.");
        }
        if (operationalDate == null) {
            throw new ReportingValidationException(
                    "Operational date is required.");
        }
        if (zoneId == null) {
            throw new ReportingValidationException(
                    "Operational time zone is required.");
        }
        if (fromInclusive == null || untilExclusive == null) {
            throw new ReportingValidationException(
                    "Dashboard timestamp boundaries are required.");
        }
        if (!fromInclusive.isBefore(untilExclusive)) {
            throw new ReportingValidationException(
                    "Dashboard timestamp interval must not be empty or inverted.");
        }
    }

    public LocalDate from() {
        return dates.from();
    }

    public LocalDate until() {
        return dates.until();
    }

    public Instant operationalDayFromInclusive() {
        return operationalDate.atStartOfDay(zoneId).toInstant();
    }

    public Instant operationalDayUntilExclusive() {
        return operationalDate.plusDays(1).atStartOfDay(zoneId).toInstant();
    }
}
