package io.github.guillermodubon.coachgym.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportingQueryWindowTest {

    @Test
    void derivesHalfOpenInstantsFromTheExplicitBusinessTimezone() {
        ReportingQueryWindow window = new ReportingQueryWindow(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2),
                ZoneId.of("America/New_York"));

        assertThat(window.fromInclusiveInstant()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        assertThat(window.toExclusiveInstant()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
        assertThat(Duration.between(
                window.fromInclusiveInstant(), window.toExclusiveInstant()))
                .isEqualTo(Duration.ofHours(25));
    }

    @Test
    void enforcesTheCalendarDayLimit() {
        new ReportingQueryWindow(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 2),
                ZoneId.of("UTC"));

        assertThatThrownBy(() -> new ReportingQueryWindow(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 3),
                ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportingQueryWindow(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1),
                ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
