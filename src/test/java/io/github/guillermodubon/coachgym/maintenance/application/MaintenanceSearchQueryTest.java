package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MaintenanceSearchQueryTest {

    @Test
    void providesStableDefaults() {
        MaintenanceSearchQuery query = MaintenanceSearchQuery.defaults();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sortField()).isEqualTo(MaintenanceSortField.SCHEDULED_ON);
        assertThat(query.direction()).isEqualTo(MaintenanceSortDirection.ASC);
    }

    @Test
    void parsesCaseInsensitiveAllowlistedSort() {
        MaintenanceSearchQuery query = MaintenanceSearchQuery.from(
                null, null, null, null, null, null, null, null,
                " Provider ", " Motor ", 1, 50, "updated_at", "desc");
        assertThat(query.providerName()).isEqualTo("Provider");
        assertThat(query.search()).isEqualTo("Motor");
        assertThat(query.sortField()).isEqualTo(MaintenanceSortField.UPDATED_AT);
        assertThat(query.direction()).isEqualTo(MaintenanceSortDirection.DESC);
    }

    @Test
    void rejectsInvalidPaginationDateRangeAndSort() {
        assertThatThrownBy(() -> new MaintenanceSearchQuery(
                null, null, null, null, null, null, null, null,
                null, null, -1, 25, null, null))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> new MaintenanceSearchQuery(
                null, null, null, null,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 9),
                null, null, null, null, 0, 25, null, null))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> MaintenanceSortField.from("sql_column"))
                .isInstanceOf(MaintenanceValidationException.class);
    }
}
