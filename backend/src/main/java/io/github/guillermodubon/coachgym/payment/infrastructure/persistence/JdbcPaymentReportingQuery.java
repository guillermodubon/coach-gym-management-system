package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentMethodDistribution;
import io.github.guillermodubon.coachgym.payment.PaymentReportingQuery;
import io.github.guillermodubon.coachgym.payment.PaymentReportingUnavailableException;
import io.github.guillermodubon.coachgym.payment.PaymentTrendGranularity;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL read adapter for canonical payment financial metrics. */
@Repository
@Transactional(readOnly = true)
class JdbcPaymentReportingQuery implements PaymentReportingQuery {

    private static final String BRANCH_PAYMENT_FILTER =
            "and p.registered_at_branch_id in (:branchIds)";

    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_PAYMENT_FILTER);

    static final String PAYMENT_METHOD_ORGANIZATION_SQL = paymentMethodSql("");
    static final String PAYMENT_METHOD_BRANCH_SQL = paymentMethodSql(BRANCH_PAYMENT_FILTER);

    static final String TREND_ORGANIZATION_SQL = trendSql("");
    static final String TREND_BRANCH_SQL = trendSql(BRANCH_PAYMENT_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public PaymentFinancialMetrics summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            List<PaymentFinancialMetrics.CurrencyMetrics> currencies = jdbcTemplate.query(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new PaymentFinancialMetrics.CurrencyMetrics(
                            resultSet.getString("currency"),
                            resultSet.getLong("paid_count"),
                            amount(resultSet.getBigDecimal("confirmed_amount")),
                            amount(resultSet.getBigDecimal("average_paid_amount")),
                            resultSet.getLong("voided_count"),
                            amount(resultSet.getBigDecimal("voided_amount")),
                            resultSet.getLong("refunded_count"),
                            amount(resultSet.getBigDecimal("refunded_amount"))));
            return new PaymentFinancialMetrics(currencies);
        } catch (DataAccessException exception) {
            throw new PaymentReportingUnavailableException(exception);
        }
    }

    @Override
    public List<PaymentMethodDistribution> paymentMethodDistribution(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            return jdbcTemplate.query(
                    scope.organizationWide()
                            ? PAYMENT_METHOD_ORGANIZATION_SQL
                            : PAYMENT_METHOD_BRANCH_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new PaymentMethodDistribution(
                            resultSet.getString("currency"),
                            PaymentMethod.valueOf(resultSet.getString("payment_method")),
                            resultSet.getLong("payment_count")));
        } catch (DataAccessException exception) {
            throw new PaymentReportingUnavailableException(exception);
        }
    }

    @Override
    public List<PaidPaymentTrendPoint> paidTrend(
            ReportingQueryScope scope,
            ReportingQueryWindow window,
            PaymentTrendGranularity granularity) {
        requireQuery(scope, window);
        Objects.requireNonNull(granularity, "Payment trend granularity is required.");
        try {
            MapSqlParameterSource parameters = parameters(scope, window)
                    .addValue("bucketUnit", bucketUnit(granularity))
                    .addValue("timezone", window.timezone().getId());
            return jdbcTemplate.query(
                    scope.organizationWide() ? TREND_ORGANIZATION_SQL : TREND_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new PaidPaymentTrendPoint(
                            resultSet.getDate("bucket_start").toLocalDate(),
                            resultSet.getString("currency"),
                            resultSet.getLong("payment_count"),
                            amount(resultSet.getBigDecimal("confirmed_amount"))));
        } catch (DataAccessException exception) {
            throw new PaymentReportingUnavailableException(exception);
        }
    }

    private static String summarySql(String branchFilter) {
        return """
                with paid as (
                    select p.currency, count(*) as paid_count,
                           coalesce(sum(p.amount), 0.00) as confirmed_amount,
                           coalesce(avg(p.amount), 0.00) as average_paid_amount
                    from gym.payments p
                    where p.status = 'PAID'
                      and p.paid_at >= :fromInclusive
                      and p.paid_at < :toExclusive
                      %s
                    group by p.currency
                ), voided as (
                    select p.currency, count(*) as voided_count,
                           coalesce(sum(p.amount), 0.00) as voided_amount
                    from gym.payments p
                    where p.status = 'VOIDED'
                      and exists (
                          select 1
                          from gym.payment_status_history correction
                          where correction.payment_id = p.id
                            and correction.previous_status = 'PAID'
                            and correction.new_status = 'VOIDED'
                            and correction.occurred_at >= :fromInclusive
                            and correction.occurred_at < :toExclusive
                      )
                      %s
                    group by p.currency
                ), refunded as (
                    select refund.currency, count(*) as refunded_count,
                           coalesce(sum(refund.amount), 0.00) as refunded_amount
                    from gym.payment_refunds refund
                    join gym.payments p on p.id = refund.payment_id
                    where p.status = 'REFUNDED'
                      and refund.refunded_at >= :fromInclusive
                      and refund.refunded_at < :toExclusive
                      %s
                    group by refund.currency
                ), currencies as (
                    select currency from paid
                    union
                    select currency from voided
                    union
                    select currency from refunded
                )
                select currencies.currency,
                       coalesce(paid.paid_count, 0) as paid_count,
                       coalesce(paid.confirmed_amount, 0.00) as confirmed_amount,
                       coalesce(paid.average_paid_amount, 0.00) as average_paid_amount,
                       coalesce(voided.voided_count, 0) as voided_count,
                       coalesce(voided.voided_amount, 0.00) as voided_amount,
                       coalesce(refunded.refunded_count, 0) as refunded_count,
                       coalesce(refunded.refunded_amount, 0.00) as refunded_amount
                from currencies
                left join paid on paid.currency = currencies.currency
                left join voided on voided.currency = currencies.currency
                left join refunded on refunded.currency = currencies.currency
                order by currencies.currency
                """.formatted(branchFilter, branchFilter, branchFilter);
    }

    private static String paymentMethodSql(String branchFilter) {
        return """
                select p.currency, p.payment_method, count(*) as payment_count
                from gym.payments p
                where p.status = 'PAID'
                  and p.paid_at >= :fromInclusive
                  and p.paid_at < :toExclusive
                  %s
                group by p.currency, p.payment_method
                order by p.currency, p.payment_method
                """.formatted(branchFilter);
    }

    private static String trendSql(String branchFilter) {
        return """
                select date_trunc(:bucketUnit, p.paid_at at time zone :timezone)::date
                           as bucket_start,
                       p.currency,
                       count(*) as payment_count,
                       coalesce(sum(p.amount), 0.00) as confirmed_amount
                from gym.payments p
                where p.status = 'PAID'
                  and p.paid_at >= :fromInclusive
                  and p.paid_at < :toExclusive
                  %s
                group by bucket_start, p.currency
                order by bucket_start, p.currency
                """.formatted(branchFilter);
    }

    private static MapSqlParameterSource parameters(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInclusive", utc(window.fromInclusiveInstant()))
                .addValue("toExclusive", utc(window.toExclusiveInstant()));
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }
        return parameters;
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String bucketUnit(PaymentTrendGranularity granularity) {
        return switch (granularity) {
            case DAILY -> "day";
            case WEEKLY -> "week";
            case MONTHLY -> "month";
        };
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void requireQuery(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        Objects.requireNonNull(scope, "Payment reporting scope is required.");
        Objects.requireNonNull(window, "Payment reporting window is required.");
    }
}
