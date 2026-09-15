package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IncidentSearchQueryTest {

    @Test
    void providesApprovedDefaults() {
        IncidentSearchQuery query = IncidentSearchQuery.defaults();

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sortField())
                .isEqualTo(IncidentSortField.REPORTED_AT);
        assertThat(query.direction())
                .isEqualTo(IncidentSortDirection.DESC);
    }

    @Test
    void normalizesBlankSearchToNull() {
        IncidentSearchQuery query = new IncidentSearchQuery(
                null,
                IncidentStatus.OPEN,
                IncidentPriority.HIGH,
                null,
                null,
                null,
                null,
                "   ",
                0,
                25,
                null,
                null);

        assertThat(query.search()).isNull();
        assertThat(query.sortField())
                .isEqualTo(IncidentSortField.REPORTED_AT);
        assertThat(query.direction())
                .isEqualTo(IncidentSortDirection.DESC);
    }

    @Test
    void rejectsInvalidPage() {
        assertThatThrownBy(() -> new IncidentSearchQuery(
                null, null, null, null, null, null, null, null,
                -1, 25, null, null))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessage("Incident page index cannot be negative.");
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> new IncidentSearchQuery(
                null, null, null, null, null, null, null, null,
                0, 101, null, null))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessage("Incident page size must be between 1 and 100.");
    }

    @Test
    void rejectsInvertedReportedRange() {
        assertThatThrownBy(() -> new IncidentSearchQuery(
                null,
                null,
                null,
                Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                null,
                null,
                null,
                0,
                25,
                null,
                null))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessageContaining("cannot be after");
    }

    @Test
    void parsesAllowlistedSortValues() {
        assertThat(IncidentSortField.from("incident-code"))
                .isEqualTo(IncidentSortField.INCIDENT_CODE);
        assertThat(IncidentSortDirection.from("asc"))
                .isEqualTo(IncidentSortDirection.ASC);
    }

    @Test
    void rejectsUnknownSortValues() {
        assertThatThrownBy(() -> IncidentSortField.from("description"))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessageContaining("Unsupported incident sort field");

        assertThatThrownBy(() -> IncidentSortDirection.from("sideways"))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessageContaining("Unsupported incident sort direction");
    }
}
