package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.client.ClientReportingUnavailableException;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
class JdbcClientReportingQueryTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void scopesTheAggregateBeforeReadingClientCounts() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcClientReportingQuery.BRANCH_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(new ClientReportingSummary(3, 2, 1, 1));
        UUID branchId = UUID.randomUUID();
        ReportingQueryWindow window = new ReportingQueryWindow(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), ZoneId.of("UTC"));

        assertThat(new JdbcClientReportingQuery(jdbcTemplate).summarize(
                ReportingQueryScope.branches(List.of(branchId)), window))
                .isEqualTo(new ClientReportingSummary(3, 2, 1, 1));

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcClientReportingQuery.BRANCH_SQL),
                parameters.capture(),
                any(RowMapper.class));
        assertThat(parameters.getValue().getValue("branchIds")).isEqualTo(List.of(branchId));
        assertThat(parameters.getValue().getValue("fromInclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void translatesDataAccessFailureWithoutReturningDatabaseDetails() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcClientReportingQuery.ORGANIZATION_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("private database detail"));

        assertThatThrownBy(() -> new JdbcClientReportingQuery(jdbcTemplate).summarize(
                ReportingQueryScope.organization(),
                new ReportingQueryWindow(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        ZoneId.of("UTC"))))
                .isInstanceOf(ClientReportingUnavailableException.class)
                .hasMessage("Client reporting data could not be read.")
                .hasCauseInstanceOf(DataRetrievalFailureException.class)
                .hasMessageNotContaining("private database detail");
    }
}
