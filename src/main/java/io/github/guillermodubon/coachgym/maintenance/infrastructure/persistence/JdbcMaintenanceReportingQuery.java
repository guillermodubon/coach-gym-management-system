package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL reader for canonical maintenance rows; history tables are not joined. */
@Repository
@Transactional(readOnly = true)
class JdbcMaintenanceReportingQuery implements MaintenanceReportingQuery {

    private static final String BRANCH_FILTER = "and m.branch_id in (:branchIds)";

    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_FILTER);
    static final String BRANCHES_ORGANIZATION_SQL = branchSummarySql("");
    static final String BRANCHES_FILTERED_SQL = branchSummarySql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcMaintenanceReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public MaintenanceReportingSummary summarize(
            ReportingQueryScope scope,
            LocalDate operationalDate) {
        requireQuery(scope, operationalDate);
        try {
            return jdbcTemplate.queryForObject(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope, operationalDate),
                    (resultSet, rowNumber) -> new MaintenanceReportingSummary(
                            resultSet.getLong("total_maintenance"),
                            resultSet.getLong("overdue_scheduled_maintenance"),
                            statusCounts(resultSet.getLong("scheduled_count"),
                                    resultSet.getLong("in_progress_count"),
                                    resultSet.getLong("completed_count"),
                                    resultSet.getLong("cancelled_count"))));
        } catch (DataAccessException exception) {
            throw new MaintenanceReportingUnavailableException(exception);
        }
    }

    @Override
    public List<MaintenanceBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            LocalDate operationalDate) {
        requireQuery(scope, operationalDate);
        try {
            return jdbcTemplate.query(
                    scope.organizationWide() ? BRANCHES_ORGANIZATION_SQL : BRANCHES_FILTERED_SQL,
                    parameters(scope, operationalDate),
                    (resultSet, rowNumber) -> new MaintenanceBranchReportingSummary(
                            resultSet.getObject("branch_id", UUID.class),
                            new MaintenanceReportingSummary(
                                    resultSet.getLong("total_maintenance"),
                                    resultSet.getLong("overdue_scheduled_maintenance"),
                                    statusCounts(resultSet.getLong("scheduled_count"),
                                            resultSet.getLong("in_progress_count"),
                                            resultSet.getLong("completed_count"),
                                            resultSet.getLong("cancelled_count")))));
        } catch (DataAccessException exception) {
            throw new MaintenanceReportingUnavailableException(exception);
        }
    }

    private static String summarySql(String branchFilter) {
        return """
                select count(*) as total_maintenance,
                       count(*) filter (
                           where m.status = 'SCHEDULED' and m.scheduled_on < :operationalDate
                       ) as overdue_scheduled_maintenance,
                       count(*) filter (where m.status = 'SCHEDULED') as scheduled_count,
                       count(*) filter (where m.status = 'IN_PROGRESS') as in_progress_count,
                       count(*) filter (where m.status = 'COMPLETED') as completed_count,
                       count(*) filter (where m.status = 'CANCELLED') as cancelled_count
                from gym.maintenances m
                where true
                  %s
                """.formatted(branchFilter);
    }

    private static String branchSummarySql(String branchFilter) {
        return """
                select m.branch_id,
                       count(*) as total_maintenance,
                       count(*) filter (
                           where m.status = 'SCHEDULED' and m.scheduled_on < :operationalDate
                       ) as overdue_scheduled_maintenance,
                       count(*) filter (where m.status = 'SCHEDULED') as scheduled_count,
                       count(*) filter (where m.status = 'IN_PROGRESS') as in_progress_count,
                       count(*) filter (where m.status = 'COMPLETED') as completed_count,
                       count(*) filter (where m.status = 'CANCELLED') as cancelled_count
                from gym.maintenances m
                where true
                  %s
                group by m.branch_id
                order by m.branch_id
                """.formatted(branchFilter);
    }

    private static Map<MaintenanceStatus, Long> statusCounts(
            long scheduled, long inProgress, long completed, long cancelled) {
        EnumMap<MaintenanceStatus, Long> counts = new EnumMap<>(MaintenanceStatus.class);
        counts.put(MaintenanceStatus.SCHEDULED, scheduled);
        counts.put(MaintenanceStatus.IN_PROGRESS, inProgress);
        counts.put(MaintenanceStatus.COMPLETED, completed);
        counts.put(MaintenanceStatus.CANCELLED, cancelled);
        return counts;
    }

    private static MapSqlParameterSource parameters(
            ReportingQueryScope scope,
            LocalDate operationalDate) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("operationalDate", operationalDate);
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }
        return parameters;
    }

    private static void requireQuery(ReportingQueryScope scope, LocalDate operationalDate) {
        Objects.requireNonNull(scope, "Maintenance reporting scope is required.");
        Objects.requireNonNull(operationalDate, "Maintenance operational date is required.");
    }
}
