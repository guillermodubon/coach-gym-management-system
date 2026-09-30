package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import io.github.guillermodubon.coachgym.payment.PaymentReportingUnavailableException;
import io.github.guillermodubon.coachgym.payment.PaymentTrendGranularity;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.math.BigDecimal;
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
class JdbcPaymentReportingQueryTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void passesResolvedBranchFiltersHalfOpenInstantsAndTrendGranularityAsParameters() {
        ReportingQueryScope scope = ReportingQueryScope.branches(List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000001")));
        ReportingQueryWindow window = new ReportingQueryWindow(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2),
                ZoneId.of("America/New_York"));
        when(jdbcTemplate.query(
                eq(JdbcPaymentReportingQuery.TREND_BRANCH_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(List.of());

        new JdbcPaymentReportingQuery(jdbcTemplate).paidTrend(
                scope, window, PaymentTrendGranularity.WEEKLY);

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                eq(JdbcPaymentReportingQuery.TREND_BRANCH_SQL),
                parameters.capture(),
                any(RowMapper.class));
        assertThat(parameters.getValue().getValue("fromInclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-11-01T04:00:00Z"));
        assertThat(parameters.getValue().getValue("toExclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-11-02T05:00:00Z"));
        assertThat(parameters.getValue().getValue("timezone")).isEqualTo("America/New_York");
        assertThat(parameters.getValue().getValue("bucketUnit")).isEqualTo("week");
        assertThat(parameters.getValue().getValue("branchIds"))
                .isEqualTo(scope.branchIds());
    }

    @Test
    void mapsDatabaseFailuresToSafePaymentReportingErrors() {
        when(jdbcTemplate.query(
                eq(JdbcPaymentReportingQuery.SUMMARY_ORGANIZATION_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("private database detail"));

        assertThatThrownBy(() -> new JdbcPaymentReportingQuery(jdbcTemplate)
                .summarize(ReportingQueryScope.organization(), window()))
                .isInstanceOf(PaymentReportingUnavailableException.class)
                .hasMessage("Payment reporting data could not be read.")
                .hasCauseInstanceOf(DataRetrievalFailureException.class)
                .hasMessageNotContaining("private database detail");
    }

    private static ReportingQueryWindow window() {
        return new ReportingQueryWindow(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                ZoneId.of("UTC"));
    }
}
