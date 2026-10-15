package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentBranchReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingQuery;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingUnavailableException;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
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

/** PostgreSQL reader for current canonical equipment status snapshots. */
@Repository
@Transactional(readOnly = true)
class JdbcEquipmentReportingQuery implements EquipmentReportingQuery {

    private static final String BRANCH_FILTER = "and e.branch_id in (:branchIds)";

    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_FILTER);
    static final String BRANCHES_ORGANIZATION_SQL = branchSummarySql("");
    static final String BRANCHES_FILTERED_SQL = branchSummarySql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcEquipmentReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public EquipmentReportingSummary summarize(ReportingQueryScope scope) {
        Objects.requireNonNull(scope, "Equipment reporting scope is required.");
        try {
            return jdbcTemplate.queryForObject(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope),
                    (resultSet, rowNumber) -> new EquipmentReportingSummary(
                            resultSet.getLong("total_equipment"),
                            resultSet.getLong("out_of_service_equipment"),
                            statusCounts(resultSet.getLong("available_count"),
                                    resultSet.getLong("maintenance_count"),
                                    resultSet.getLong("out_of_service_count"),
                                    resultSet.getLong("retired_count"))));
        } catch (DataAccessException exception) {
            throw new EquipmentReportingUnavailableException(exception);
        }
    }

    @Override
    public List<EquipmentBranchReportingSummary> summarizeByBranch(ReportingQueryScope scope) {
        Objects.requireNonNull(scope, "Equipment reporting scope is required.");
        try {
            return jdbcTemplate.query(
                    scope.organizationWide() ? BRANCHES_ORGANIZATION_SQL : BRANCHES_FILTERED_SQL,
                    parameters(scope),
                    (resultSet, rowNumber) -> new EquipmentBranchReportingSummary(
                            resultSet.getObject("branch_id", UUID.class),
                            new EquipmentReportingSummary(
                                    resultSet.getLong("total_equipment"),
                                    resultSet.getLong("out_of_service_equipment"),
                                    statusCounts(resultSet.getLong("available_count"),
                                            resultSet.getLong("maintenance_count"),
                                            resultSet.getLong("out_of_service_count"),
                                            resultSet.getLong("retired_count")))));
        } catch (DataAccessException exception) {
            throw new EquipmentReportingUnavailableException(exception);
        }
    }

    private static String summarySql(String branchFilter) {
        return """
                select count(*) as total_equipment,
                       count(*) filter (where e.status = 'OUT_OF_SERVICE') as out_of_service_equipment,
                       count(*) filter (where e.status = 'AVAILABLE') as available_count,
                       count(*) filter (where e.status = 'MAINTENANCE') as maintenance_count,
                       count(*) filter (where e.status = 'OUT_OF_SERVICE') as out_of_service_count,
                       count(*) filter (where e.status = 'RETIRED') as retired_count
                from gym.equipment e
                where true
                  %s
                """.formatted(branchFilter);
    }

    private static String branchSummarySql(String branchFilter) {
        return """
                select e.branch_id,
                       count(*) as total_equipment,
                       count(*) filter (where e.status = 'OUT_OF_SERVICE') as out_of_service_equipment,
                       count(*) filter (where e.status = 'AVAILABLE') as available_count,
                       count(*) filter (where e.status = 'MAINTENANCE') as maintenance_count,
                       count(*) filter (where e.status = 'OUT_OF_SERVICE') as out_of_service_count,
                       count(*) filter (where e.status = 'RETIRED') as retired_count
                from gym.equipment e
                where true
                  %s
                group by e.branch_id
                order by e.branch_id
                """.formatted(branchFilter);
    }

    private static Map<EquipmentStatus, Long> statusCounts(
            long available, long maintenance, long outOfService, long retired) {
        EnumMap<EquipmentStatus, Long> counts = new EnumMap<>(EquipmentStatus.class);
        counts.put(EquipmentStatus.AVAILABLE, available);
        counts.put(EquipmentStatus.MAINTENANCE, maintenance);
        counts.put(EquipmentStatus.OUT_OF_SERVICE, outOfService);
        counts.put(EquipmentStatus.RETIRED, retired);
        return counts;
    }

    private static MapSqlParameterSource parameters(ReportingQueryScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }
        return parameters;
    }
}
