package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Resolves optional dashboard dates against the configured application clock. */
@Component
public class DashboardPeriodResolver {

    private final Clock clock;
    private final ReportingProperties properties;

    public DashboardPeriodResolver(
            Clock clock,
            ReportingProperties properties) {
        this.clock = Objects.requireNonNull(clock, "Application clock is required.");
        this.properties = Objects.requireNonNull(
                properties, "Reporting properties are required.");
    }

    public DashboardPeriod resolve(DashboardQuery query) {
        DashboardQuery safeQuery = query == null
                ? DashboardQuery.defaults()
                : query;

        ZoneId zoneId = clock.getZone();
        LocalDate operationalDate = LocalDate.now(clock);
        LocalDate from = resolveFrom(safeQuery, operationalDate);
        LocalDate until = resolveUntil(safeQuery, operationalDate);

        validate(from, until, operationalDate, properties.getMaximumPeriodDays());

        DashboardPeriodDetails dates = new DashboardPeriodDetails(from, until);
        return new DashboardPeriod(
                dates,
                operationalDate,
                zoneId,
                from.atStartOfDay(zoneId).toInstant(),
                until.plusDays(1).atStartOfDay(zoneId).toInstant());
    }

    private static LocalDate resolveFrom(
            DashboardQuery query,
            LocalDate operationalDate) {
        if (query.from() != null) {
            return query.from();
        }
        LocalDate reference = query.until() == null
                ? operationalDate
                : query.until();
        return reference.withDayOfMonth(1);
    }

    private static LocalDate resolveUntil(
            DashboardQuery query,
            LocalDate operationalDate) {
        return query.until() == null
                ? operationalDate
                : query.until();
    }

    private static void validate(
            LocalDate from,
            LocalDate until,
            LocalDate operationalDate,
            int maximumPeriodDays) {
        if (from.isAfter(until)) {
            throw new ReportingValidationException(
                    "Dashboard period start date must not be after end date.");
        }
        if (until.isAfter(operationalDate)) {
            throw new ReportingValidationException(
                    "Dashboard period end date must not be after the operational date.");
        }

        long inclusiveDays = ChronoUnit.DAYS.between(from, until) + 1;
        if (inclusiveDays > maximumPeriodDays) {
            throw new ReportingValidationException(
                    "Dashboard period must not exceed "
                            + maximumPeriodDays
                            + " inclusive days.");
        }
    }
}
