package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DashboardPeriodTest {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/El_Salvador");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-05T18:00:00Z"),
            GYM_ZONE);

    @Test
    void defaultsToCurrentOperationalMonth() {
        DashboardPeriod period = resolver(366).resolve(DashboardQuery.defaults());

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(period.until()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(period.operationalDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(period.zoneId()).isEqualTo(GYM_ZONE);
        assertThat(period.fromInclusive())
                .isEqualTo(Instant.parse("2026-09-01T06:00:00Z"));
        assertThat(period.untilExclusive())
                .isEqualTo(Instant.parse("2026-09-06T06:00:00Z"));
    }

    @Test
    void defaultsMissingUntilToOperationalDate() {
        DashboardPeriod period = resolver(366).resolve(new DashboardQuery(
                LocalDate.of(2026, 8, 15), null));
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(period.until()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void defaultsMissingFromToFirstDayOfUntilMonth() {
        DashboardPeriod period = resolver(366).resolve(new DashboardQuery(
                null, LocalDate.of(2026, 8, 20)));
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(period.until()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void exposesOperationalDayTimestampBoundaries() {
        DashboardPeriod period = resolver(366).resolve(DashboardQuery.defaults());
        assertThat(period.operationalDayFromInclusive())
                .isEqualTo(Instant.parse("2026-09-05T06:00:00Z"));
        assertThat(period.operationalDayUntilExclusive())
                .isEqualTo(Instant.parse("2026-09-06T06:00:00Z"));
    }

    @Test
    void rejectsInvertedFutureAndOversizedPeriods() {
        DashboardPeriodResolver resolver = resolver(30);

        assertThatThrownBy(() -> resolver.resolve(new DashboardQuery(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 4))))
                .isInstanceOf(ReportingValidationException.class);

        assertThatThrownBy(() -> resolver.resolve(new DashboardQuery(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 6))))
                .isInstanceOf(ReportingValidationException.class);

        assertThatThrownBy(() -> resolver.resolve(new DashboardQuery(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 5))))
                .isInstanceOf(ReportingValidationException.class);
    }

    @Test
    void acceptsMaximumInclusivePeriodExactly() {
        DashboardPeriod period = resolver(31).resolve(new DashboardQuery(
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 9, 5)));
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(period.until()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    private static DashboardPeriodResolver resolver(int maximumDays) {
        ReportingProperties properties = new ReportingProperties();
        properties.setMaximumPeriodDays(maximumDays);
        return new DashboardPeriodResolver(CLOCK, properties);
    }
}
