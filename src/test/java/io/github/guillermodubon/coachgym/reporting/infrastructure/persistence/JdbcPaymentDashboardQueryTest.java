package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardCurrencyConflictException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import java.math.BigDecimal;
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
class JdbcPaymentDashboardQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void returnsEffectivePaidRevenueForExpectedCurrency() {
        whenAggregate(new JdbcPaymentDashboardQuery.PaymentAggregate(
                3L,
                new BigDecimal("180.75"),
                1,
                "USD",
                "USD"));

        PaymentDashboardDetails result =
                new JdbcPaymentDashboardQuery(jdbcTemplate)
                        .summarize(period(), " usd ");

        assertThat(result.paidCount()).isEqualTo(3);
        assertThat(result.registeredAmount()).isEqualByComparingTo("180.75");
        assertThat(result.currency()).isEqualTo("USD");

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcPaymentDashboardQuery.SQL),
                parameters.capture(),
                any(RowMapper.class));

        assertThat(parameters.getValue().getValue("fromInclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-09-01T06:00:00Z"));
        assertThat(parameters.getValue().getValue("untilExclusive"))
                .isEqualTo(OffsetDateTime.parse("2026-09-06T06:00:00Z"));
    }

    @Test
    void returnsZeroAmountWithConfiguredCurrencyWhenPeriodHasNoPaidRows() {
        whenAggregate(new JdbcPaymentDashboardQuery.PaymentAggregate(
                0L,
                new BigDecimal("0.00"),
                0,
                null,
                null));

        PaymentDashboardDetails result =
                new JdbcPaymentDashboardQuery(jdbcTemplate)
                        .summarize(period(), "USD");

        assertThat(result.paidCount()).isZero();
        assertThat(result.registeredAmount()).isEqualByComparingTo("0.00");
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsMultipleCurrenciesWithoutCombiningAmounts() {
        whenAggregate(new JdbcPaymentDashboardQuery.PaymentAggregate(
                2L,
                new BigDecimal("150.00"),
                2,
                "EUR",
                "EUR,USD"));

        assertThatThrownBy(() ->
                new JdbcPaymentDashboardQuery(jdbcTemplate)
                        .summarize(period(), "USD"))
                .isInstanceOf(DashboardCurrencyConflictException.class)
                .satisfies(exception -> assertThat(
                        ((DashboardCurrencyConflictException) exception)
                                .currencies())
                        .containsExactlyInAnyOrder("EUR", "USD"));
    }

    @Test
    void rejectsSingleUnexpectedCurrency() {
        whenAggregate(new JdbcPaymentDashboardQuery.PaymentAggregate(
                1L,
                new BigDecimal("50.00"),
                1,
                "EUR",
                "EUR"));

        assertThatThrownBy(() ->
                new JdbcPaymentDashboardQuery(jdbcTemplate)
                        .summarize(period(), "USD"))
                .isInstanceOf(DashboardCurrencyConflictException.class);
    }

    @Test
    void validatesExpectedCurrencyBeforeDatabaseAccess() {
        JdbcPaymentDashboardQuery query =
                new JdbcPaymentDashboardQuery(jdbcTemplate);

        assertThatThrownBy(() -> query.summarize(period(), null))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> query.summarize(period(), "US"))
                .isInstanceOf(ReportingValidationException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void translatesDatabaseFailureToSafeReportingException() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcPaymentDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("database detail"));

        assertThatThrownBy(() ->
                new JdbcPaymentDashboardQuery(jdbcTemplate)
                        .summarize(period(), "USD"))
                .isInstanceOf(DashboardDataAccessException.class)
                .hasMessage("Payment dashboard metrics could not be read.")
                .hasCauseInstanceOf(DataRetrievalFailureException.class);
    }

    private void whenAggregate(
            JdbcPaymentDashboardQuery.PaymentAggregate aggregate) {
        when(jdbcTemplate.queryForObject(
                eq(JdbcPaymentDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(aggregate);
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
