package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryBranchReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingQuery;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingUnavailableException;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL reader for safe aggregates over logical deliveries and separate attempts. */
@Repository
@Transactional(readOnly = true)
class JdbcEmailDeliveryReportingQuery implements EmailDeliveryReportingQuery {

    private static final String BRANCH_FILTER = "and d.branch_id in (:branchIds)";

    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_FILTER);
    static final String BRANCHES_ORGANIZATION_SQL = branchSummarySql("");
    static final String BRANCHES_FILTERED_SQL = branchSummarySql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcEmailDeliveryReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public EmailDeliveryReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            return toSummary(jdbcTemplate.query(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope, window),
                    JdbcEmailDeliveryReportingQuery::mapRow));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new EmailDeliveryReportingUnavailableException(exception);
        }
    }

    @Override
    public List<EmailDeliveryBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            List<MetricRow> rows = jdbcTemplate.query(
                    scope.organizationWide() ? BRANCHES_ORGANIZATION_SQL : BRANCHES_FILTERED_SQL,
                    parameters(scope, window),
                    JdbcEmailDeliveryReportingQuery::mapRow);
            Map<UUID, List<MetricRow>> grouped = new LinkedHashMap<>();
            rows.forEach(row -> grouped.computeIfAbsent(
                    row.branchId(), ignored -> new ArrayList<>()).add(row));
            return grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new EmailDeliveryBranchReportingSummary(
                            entry.getKey(), toSummary(entry.getValue())))
                    .toList();
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new EmailDeliveryReportingUnavailableException(exception);
        }
    }

    private static EmailDeliveryReportingSummary toSummary(List<MetricRow> rows) {
        MetricRow total = rows.stream()
                .filter(row -> "TOTAL".equals(row.metricKind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Email delivery aggregate total is missing."));
        EnumMap<EmailDeliveryType, Long> types = new EnumMap<>(EmailDeliveryType.class);
        EnumMap<EmailDeliveryFailureCode, Long> failures =
                new EnumMap<>(EmailDeliveryFailureCode.class);
        long retryAttempts = 0;
        for (MetricRow row : rows) {
            if ("TYPE".equals(row.metricKind())) {
                types.put(EmailDeliveryType.valueOf(row.metricValue()), row.metricCount());
            } else if ("FAILURE".equals(row.metricKind())) {
                failures.put(EmailDeliveryFailureCode.valueOf(row.metricValue()), row.metricCount());
            } else if ("RETRY".equals(row.metricKind())) {
                retryAttempts = row.metricCount();
            }
        }
        return new EmailDeliveryReportingSummary(
                total.totalDeliveries(), total.pendingDeliveries(), total.sentDeliveries(),
                total.failedDeliveries(), retryAttempts, types, failures);
    }

    private static String summarySql(String branchFilter) {
        return """
                with scoped_deliveries as materialized (
                    select d.delivery_type, d.status
                    from gym.email_deliveries d
                    where d.requested_at >= :fromInclusive
                      and d.requested_at < :toExclusive
                      and d.delivery_type in ('PAYMENT_RECEIPT', 'ACCESS_CREDENTIAL')
                      %s
                ), scoped_attempts as materialized (
                    select a.attempt_number, a.result, a.failure_code
                    from gym.email_deliveries d
                    cross join lateral (
                        select attempt.attempt_number, attempt.result, attempt.failure_code
                        from gym.email_delivery_attempts attempt
                        where attempt.delivery_id = d.id
                          and attempt.started_at >= :fromInclusive
                          and attempt.started_at < :toExclusive
                    ) a
                    where d.delivery_type in ('PAYMENT_RECEIPT', 'ACCESS_CREDENTIAL')
                      %s
                ), totals as (
                    select count(*) as total_deliveries,
                           count(*) filter (where status = 'PENDING') as pending_deliveries,
                           count(*) filter (where status = 'SENT') as sent_deliveries,
                           count(*) filter (where status = 'FAILED') as failed_deliveries
                    from scoped_deliveries
                ), retries as (
                    select count(*) filter (where attempt_number > 1) as retry_attempts
                    from scoped_attempts
                )
                select null::uuid as branch_id,
                       'TOTAL' as metric_kind, null::text as metric_value,
                       totals.total_deliveries, totals.pending_deliveries,
                       totals.sent_deliveries, totals.failed_deliveries,
                       0::bigint as metric_count
                from totals
                union all
                select null::uuid, 'TYPE', delivery_type, 0, 0, 0, 0, count(*)
                from scoped_deliveries group by delivery_type
                union all
                select null::uuid, 'RETRY', null::text, 0, 0, 0, 0, retries.retry_attempts
                from retries
                union all
                select null::uuid, 'FAILURE', failure_code, 0, 0, 0, 0, count(*)
                from scoped_attempts
                where result in ('FAILED', 'AMBIGUOUS') and failure_code is not null
                group by failure_code
                """.formatted(branchFilter, branchFilter);
    }

    private static String branchSummarySql(String branchFilter) {
        return """
                with scoped_deliveries as materialized (
                    select d.branch_id, d.delivery_type, d.status
                    from gym.email_deliveries d
                    where d.requested_at >= :fromInclusive
                      and d.requested_at < :toExclusive
                      and d.delivery_type in ('PAYMENT_RECEIPT', 'ACCESS_CREDENTIAL')
                      %s
                ), scoped_attempts as materialized (
                    select d.branch_id, a.attempt_number, a.result, a.failure_code
                    from gym.email_deliveries d
                    cross join lateral (
                        select attempt.attempt_number, attempt.result, attempt.failure_code
                        from gym.email_delivery_attempts attempt
                        where attempt.delivery_id = d.id
                          and attempt.started_at >= :fromInclusive
                          and attempt.started_at < :toExclusive
                    ) a
                    where d.delivery_type in ('PAYMENT_RECEIPT', 'ACCESS_CREDENTIAL')
                      %s
                ), branch_ids as (
                    select branch_id from scoped_deliveries
                    union
                    select branch_id from scoped_attempts
                ), delivery_totals as (
                    select branch_id, count(*) as total_deliveries,
                           count(*) filter (where status = 'PENDING') as pending_deliveries,
                           count(*) filter (where status = 'SENT') as sent_deliveries,
                           count(*) filter (where status = 'FAILED') as failed_deliveries
                    from scoped_deliveries group by branch_id
                ), retries as (
                    select branch_id,
                           count(*) filter (where attempt_number > 1) as retry_attempts
                    from scoped_attempts group by branch_id
                )
                select branch_ids.branch_id, 'TOTAL' as metric_kind,
                       null::text as metric_value,
                       coalesce(delivery_totals.total_deliveries, 0)::bigint as total_deliveries,
                       coalesce(delivery_totals.pending_deliveries, 0)::bigint as pending_deliveries,
                       coalesce(delivery_totals.sent_deliveries, 0)::bigint as sent_deliveries,
                       coalesce(delivery_totals.failed_deliveries, 0)::bigint as failed_deliveries,
                       0::bigint as metric_count
                from branch_ids
                left join delivery_totals using (branch_id)
                union all
                select branch_id, 'TYPE', delivery_type, 0, 0, 0, 0, count(*)
                from scoped_deliveries group by branch_id, delivery_type
                union all
                select branch_ids.branch_id, 'RETRY', null::text, 0, 0, 0, 0,
                       coalesce(retries.retry_attempts, 0)::bigint
                from branch_ids left join retries using (branch_id)
                union all
                select branch_id, 'FAILURE', failure_code, 0, 0, 0, 0, count(*)
                from scoped_attempts
                where result in ('FAILED', 'AMBIGUOUS') and failure_code is not null
                group by branch_id, failure_code
                order by branch_id, metric_kind, metric_value
                """.formatted(branchFilter, branchFilter);
    }

    private static MapSqlParameterSource parameters(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInclusive", OffsetDateTime.ofInstant(
                        window.fromInclusiveInstant(), ZoneOffset.UTC))
                .addValue("toExclusive", OffsetDateTime.ofInstant(
                        window.toExclusiveInstant(), ZoneOffset.UTC));
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }
        return parameters;
    }

    private static MetricRow mapRow(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new MetricRow(
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getString("metric_kind"),
                resultSet.getString("metric_value"),
                resultSet.getLong("metric_count"),
                resultSet.getLong("total_deliveries"),
                resultSet.getLong("pending_deliveries"),
                resultSet.getLong("sent_deliveries"),
                resultSet.getLong("failed_deliveries"));
    }

    private static void requireQuery(ReportingQueryScope scope, ReportingQueryWindow window) {
        Objects.requireNonNull(scope, "Email delivery reporting scope is required.");
        Objects.requireNonNull(window, "Email delivery reporting window is required.");
    }

    private record MetricRow(
            UUID branchId,
            String metricKind,
            String metricValue,
            long metricCount,
            long totalDeliveries,
            long pendingDeliveries,
            long sentDeliveries,
            long failedDeliveries) {
    }
}
