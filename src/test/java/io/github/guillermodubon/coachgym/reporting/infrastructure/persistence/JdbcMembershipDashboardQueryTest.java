package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import java.time.Instant;
import java.time.LocalDate;
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
class JdbcMembershipDashboardQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void returnsAggregatedMembershipMetricsAndExpectedWindow() {
        MembershipDashboardDetails expected =
                new MembershipDashboardDetails(12, 3, 4);
        when(jdbcTemplate.queryForObject(
                eq(JdbcMembershipDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(expected);

        MembershipDashboardDetails result =
                new JdbcMembershipDashboardQuery(jdbcTemplate)
                        .summarize(period(), 7);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcMembershipDashboardQuery.SQL),
                parameters.capture(),
                any(RowMapper.class));

        assertThat(parameters.getValue().getValue("operationalDate"))
                .isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(parameters.getValue().getValue("expirationUntil"))
                .isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    void acceptsZeroDayExpirationWindow() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcMembershipDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(new MembershipDashboardDetails(0, 0, 0));

        new JdbcMembershipDashboardQuery(jdbcTemplate)
                .summarize(period(), 0);

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcMembershipDashboardQuery.SQL),
                parameters.capture(),
                any(RowMapper.class));
        assertThat(parameters.getValue().getValue("expirationUntil"))
                .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void rejectsInvalidWarningWindowBeforeDatabaseAccess() {
        JdbcMembershipDashboardQuery query =
                new JdbcMembershipDashboardQuery(jdbcTemplate);

        assertThatThrownBy(() -> query.summarize(period(), -1))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> query.summarize(period(), 91))
                .isInstanceOf(ReportingValidationException.class);
    }

    @Test
    void translatesDatabaseFailureToSafeReportingException() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcMembershipDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("database detail"));

        assertThatThrownBy(() ->
                new JdbcMembershipDashboardQuery(jdbcTemplate)
                        .summarize(period(), 7))
                .isInstanceOf(DashboardDataAccessException.class)
                .hasMessage("Membership dashboard metrics could not be read.")
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
