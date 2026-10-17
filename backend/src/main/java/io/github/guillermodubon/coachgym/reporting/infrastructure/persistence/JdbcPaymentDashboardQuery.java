package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardCurrencyConflictException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import io.github.guillermodubon.coachgym.reporting.application.PaymentDashboardQuery;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for effective paid revenue. */
@Repository
class JdbcPaymentDashboardQuery implements PaymentDashboardQuery {

    static final String SQL = """
            select
                count(*) as paid_count,
                coalesce(sum(p.amount), 0.00) as registered_amount,
                count(distinct p.currency) as currency_count,
                min(p.currency) as single_currency,
                string_agg(distinct p.currency, ',' order by p.currency)
                    as currencies
            from gym.payments p
            where p.status = 'PAID'
              and p.paid_at >= :fromInclusive
              and p.paid_at < :untilExclusive
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentDashboardQuery(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "Named parameter JDBC template is required.");
    }

    @Override
    public PaymentDashboardDetails summarize(
            DashboardPeriod period,
            String expectedCurrency) {
        Objects.requireNonNull(period, "Dashboard period is required.");
        String normalizedCurrency = normalizeCurrency(expectedCurrency);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(
                        "fromInclusive",
                        OffsetDateTime.ofInstant(
                                period.fromInclusive(),
                                ZoneOffset.UTC))
                .addValue(
                        "untilExclusive",
                        OffsetDateTime.ofInstant(
                                period.untilExclusive(),
                                ZoneOffset.UTC));

        try {
            PaymentAggregate aggregate = jdbcTemplate.queryForObject(
                    SQL,
                    parameters,
                    (resultSet, rowNumber) -> new PaymentAggregate(
                            resultSet.getLong("paid_count"),
                            resultSet.getBigDecimal("registered_amount"),
                            resultSet.getInt("currency_count"),
                            resultSet.getString("single_currency"),
                            resultSet.getString("currencies")));

            if (aggregate == null) {
                throw new DashboardDataAccessException(
                        "Payment dashboard metrics could not be read.",
                        null);
            }

            validateCurrencies(aggregate, normalizedCurrency);

            BigDecimal amount = aggregate.registeredAmount() == null
                    ? BigDecimal.ZERO.setScale(2)
                    : aggregate.registeredAmount();

            return new PaymentDashboardDetails(
                    aggregate.paidCount(),
                    amount,
                    normalizedCurrency);
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Payment dashboard metrics could not be read.",
                    exception);
        }
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new ReportingValidationException(
                    "Expected dashboard currency is required.");
        }
        String normalized = currency.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ReportingValidationException(
                    "Expected dashboard currency must be a three-letter ISO code.");
        }
        return normalized;
    }

    private static void validateCurrencies(
            PaymentAggregate aggregate,
            String expectedCurrency) {
        if (aggregate.currencyCount() == 0) {
            return;
        }

        if (aggregate.currencyCount() > 1
                || !expectedCurrency.equals(aggregate.singleCurrency())) {
            throw new DashboardCurrencyConflictException(
                    currencies(aggregate.currencies()));
        }
    }

    private static Set<String> currencies(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(currency -> !currency.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    record PaymentAggregate(
            long paidCount,
            BigDecimal registeredAmount,
            int currencyCount,
            String singleCurrency,
            String currencies) {
    }
}
