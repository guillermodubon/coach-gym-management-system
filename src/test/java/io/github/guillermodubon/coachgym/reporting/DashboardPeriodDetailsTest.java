package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardPeriodDetailsTest {

    @Test
    void acceptsInclusiveOrderedPeriod() {
        DashboardPeriodDetails period = new DashboardPeriodDetails(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5));
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(period.until()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void acceptsSingleDayPeriod() {
        LocalDate date = LocalDate.of(2026, 9, 5);
        DashboardPeriodDetails period = new DashboardPeriodDetails(date, date);
        assertThat(period.from()).isEqualTo(period.until());
    }

    @Test
    void rejectsMissingOrInvertedPeriod() {
        LocalDate date = LocalDate.of(2026, 9, 5);
        assertThatThrownBy(() -> new DashboardPeriodDetails(null, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardPeriodDetails(date, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardPeriodDetails(
                date, date.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
