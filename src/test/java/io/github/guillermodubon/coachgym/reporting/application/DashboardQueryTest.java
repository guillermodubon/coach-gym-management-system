package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardQueryTest {

    @Test
    void createsEmptyDefaultQuery() {
        DashboardQuery query = DashboardQuery.defaults();
        assertThat(query.from()).isNull();
        assertThat(query.until()).isNull();
    }

    @Test
    void preservesExplicitDates() {
        DashboardQuery query = new DashboardQuery(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5));
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(query.until()).isEqualTo(LocalDate.of(2026, 9, 5));
    }
}
