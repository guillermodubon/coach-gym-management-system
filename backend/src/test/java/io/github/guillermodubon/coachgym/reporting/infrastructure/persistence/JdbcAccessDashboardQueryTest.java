package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcAccessDashboardQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void returnsTodayMetricsWithHalfOpenTimestampBoundaries() {
        AccessDashboardDetails expected = new AccessDashboardDetails(25, 2);
        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(expected);

        AccessDashboardDetails result =
                new JdbcAccessDashboardQuery(jdbcTemplate)
                        .summarizeToday(period());

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcAccessDashboardQuery.SQL),
                parameters.capture(),
                any(RowMapper.class));

        assertThat(parameters.getValue().getValue("dayFromInclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-09-05T06:00:00Z"));
        assertThat(parameters.getValue().getValue("dayUntilExclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-09-06T06:00:00Z"));
    }

    @Test
    void translatesDatabaseFailureToSafeReportingException() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("database detail"));

        assertThatThrownBy(() ->
                new JdbcAccessDashboardQuery(jdbcTemplate)
                        .summarizeToday(period()))
                .isInstanceOf(DashboardDataAccessException.class)
                .hasMessage("Access dashboard metrics could not be read.")
                .hasCauseInstanceOf(DataRetrievalFailureException.class);
    }

    private static DashboardPeriod period() {
        ZoneId zone = ZoneId.of("America/El_Salvador");
        return new DashboardPeriod(
                new DashboardPeriodDetails(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 5)),
                LocalDate.of(2026, 9, 5),
                zone,
                Instant.parse("2026-09-01T06:00:00Z"),
                Instant.parse("2026-09-06T06:00:00Z"));
    }
}
