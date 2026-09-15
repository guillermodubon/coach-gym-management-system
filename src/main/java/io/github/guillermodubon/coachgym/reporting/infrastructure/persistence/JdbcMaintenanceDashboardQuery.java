package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.MaintenanceDashboardQuery;
import java.time.LocalDate;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for current maintenance work-order metrics. */
@Repository
class JdbcMaintenanceDashboardQuery implements MaintenanceDashboardQuery {

    static final String SQL = """
            select
                count(*) filter (where status = 'SCHEDULED') as scheduled_count,
                count(*) filter (where status = 'IN_PROGRESS') as in_progress_count,
                count(*) filter (
                    where status = 'SCHEDULED'
                      and scheduled_on < :operationalDate
                ) as overdue_count
            from gym.maintenances
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcMaintenanceDashboardQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public MaintenanceDashboardDetails summarize(LocalDate operationalDate) {
        Objects.requireNonNull(operationalDate, "Operational date is required.");
        try {
            MaintenanceDashboardDetails result = jdbcTemplate.queryForObject(
                    SQL, new MapSqlParameterSource(
                            "operationalDate", operationalDate),
                    (rs, row) -> new MaintenanceDashboardDetails(
                            rs.getLong("scheduled_count"),
                            rs.getLong("in_progress_count"),
                            rs.getLong("overdue_count")));
            if (result == null) {
                throw new DashboardDataAccessException(
                        "Maintenance dashboard metrics could not be read.", null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Maintenance dashboard metrics could not be read.", exception);
        }
    }
}
