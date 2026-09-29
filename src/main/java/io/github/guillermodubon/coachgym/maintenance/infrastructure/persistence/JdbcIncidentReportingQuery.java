package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.IncidentBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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

/** PostgreSQL reader for canonical incident rows; history rows are never joined. */
@Repository
@Transactional(readOnly = true)
class JdbcIncidentReportingQuery implements IncidentReportingQuery {

    private static final String BRANCH_FILTER = "and i.branch_id in (:branchIds)";

    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_FILTER);
    static final String BRANCHES_ORGANIZATION_SQL = branchSummarySql("");
    static final String BRANCHES_FILTERED_SQL = branchSummarySql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcIncidentReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public IncidentReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            List<MetricRow> rows = jdbcTemplate.query(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new MetricRow(
                            null,
                            resultSet.getString("metric_kind"),
                            resultSet.getString("metric_value"),
                            resultSet.getLong("metric_count"),
                            resultSet.getLong("total_incidents"),
                            resultSet.getLong("open_incidents"),
                            resultSet.getLong("created_in_range"),
                            resultSet.getLong("resolved_in_range")));
            return toSummary(rows);
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new IncidentReportingUnavailableException(exception);
        }
    }

    @Override
    public List<IncidentBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            List<MetricRow> rows = jdbcTemplate.query(
                    scope.organizationWide() ? BRANCHES_ORGANIZATION_SQL : BRANCHES_FILTERED_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new MetricRow(
                            resultSet.getObject("branch_id", UUID.class),
                            resultSet.getString("metric_kind"),
                            resultSet.getString("metric_value"),
                            resultSet.getLong("metric_count"),
                            resultSet.getLong("total_incidents"),
                            resultSet.getLong("open_incidents"),
                            resultSet.getLong("created_in_range"),
                            resultSet.getLong("resolved_in_range")));
            Map<UUID, List<MetricRow>> grouped = new LinkedHashMap<>();
            rows.forEach(row -> grouped.computeIfAbsent(row.branchId(), ignored -> new ArrayList<>()).add(row));
            return grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new IncidentBranchReportingSummary(
                            entry.getKey(), toSummary(entry.getValue())))
                    .toList();
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new IncidentReportingUnavailableException(exception);
        }
    }

    private static IncidentReportingSummary toSummary(List<MetricRow> rows) {
        MetricRow total = rows.stream()
                .filter(row -> "TOTAL".equals(row.metricKind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Incident aggregate total is missing."));
        EnumMap<IncidentStatus, Long> statuses = new EnumMap<>(IncidentStatus.class);
        EnumMap<IncidentPriority, Long> priorities = new EnumMap<>(IncidentPriority.class);
        rows.stream().filter(row -> "STATUS".equals(row.metricKind()))
                .forEach(row -> statuses.put(IncidentStatus.valueOf(row.metricValue()), row.metricCount()));
        rows.stream().filter(row -> "PRIORITY".equals(row.metricKind()))
                .forEach(row -> priorities.put(IncidentPriority.valueOf(row.metricValue()), row.metricCount()));
        return new IncidentReportingSummary(
                total.totalIncidents(), total.openIncidents(), total.createdInRange(),
                total.resolvedInRange(), statuses, priorities);
    }

    private static String summarySql(String branchFilter) {
        return """
                with scoped as (
                    select i.status, i.priority, i.reported_at, i.resolved_at
                    from gym.incidents i
                    where true
                      %s
                ), totals as (
                    select count(*) as total_incidents,
                           count(*) filter (where status in ('OPEN', 'IN_PROGRESS')) as open_incidents,
                           count(*) filter (
                               where reported_at >= :fromInclusive and reported_at < :toExclusive
                           ) as created_in_range,
                           count(*) filter (
                               where resolved_at >= :fromInclusive and resolved_at < :toExclusive
                           ) as resolved_in_range
                    from scoped
                )
                select 'TOTAL' as metric_kind, null::text as metric_value, 0::bigint as metric_count,
                       totals.total_incidents, totals.open_incidents,
                       totals.created_in_range, totals.resolved_in_range
                from totals
                union all
                select 'STATUS', status, count(*), 0, 0, 0, 0
                from scoped group by status
                union all
                select 'PRIORITY', priority, count(*), 0, 0, 0, 0
                from scoped group by priority
                """.formatted(branchFilter);
    }

    private static String branchSummarySql(String branchFilter) {
        return """
                with scoped as (
                    select i.branch_id, i.status, i.priority, i.reported_at, i.resolved_at
                    from gym.incidents i
                    where true
                      %s
                ), totals as (
                    select branch_id, count(*) as total_incidents,
                           count(*) filter (where status in ('OPEN', 'IN_PROGRESS')) as open_incidents,
                           count(*) filter (
                               where reported_at >= :fromInclusive and reported_at < :toExclusive
                           ) as created_in_range,
                           count(*) filter (
                               where resolved_at >= :fromInclusive and resolved_at < :toExclusive
                           ) as resolved_in_range
                    from scoped group by branch_id
                )
                select totals.branch_id, 'TOTAL' as metric_kind, null::text as metric_value,
                       0::bigint as metric_count, totals.total_incidents, totals.open_incidents,
                       totals.created_in_range, totals.resolved_in_range
                from totals
                union all
                select branch_id, 'STATUS', status, count(*), 0, 0, 0, 0
                from scoped group by branch_id, status
                union all
                select branch_id, 'PRIORITY', priority, count(*), 0, 0, 0, 0
                from scoped group by branch_id, priority
                order by branch_id, metric_kind, metric_value
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

    private static void requireQuery(ReportingQueryScope scope, ReportingQueryWindow window) {
        Objects.requireNonNull(scope, "Incident reporting scope is required.");
        Objects.requireNonNull(window, "Incident reporting window is required.");
    }

    private record MetricRow(
            UUID branchId,
            String metricKind,
            String metricValue,
            long metricCount,
            long totalIncidents,
            long openIncidents,
            long createdInRange,
            long resolvedInRange) {
    }
}
