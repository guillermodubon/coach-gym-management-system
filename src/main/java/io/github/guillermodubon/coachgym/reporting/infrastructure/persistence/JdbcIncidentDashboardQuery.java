package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.IncidentDashboardQuery;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for unresolved incident metrics. */
@Repository
class JdbcIncidentDashboardQuery implements IncidentDashboardQuery {

    static final String SQL = """
            select
                count(*) filter (where status = 'OPEN') as open_count,
                count(*) filter (where status = 'IN_PROGRESS') as in_progress_count,
                count(*) filter (
                    where priority = 'CRITICAL'
                      and status in ('OPEN', 'IN_PROGRESS')
                ) as critical_open_count
            from gym.incidents
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcIncidentDashboardQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public IncidentDashboardDetails summarize() {
        try {
            IncidentDashboardDetails result = jdbcTemplate.queryForObject(
                    SQL, new MapSqlParameterSource(),
                    (rs, row) -> new IncidentDashboardDetails(
                            rs.getLong("open_count"),
                            rs.getLong("in_progress_count"),
                            rs.getLong("critical_open_count")));
            if (result == null) {
                throw new DashboardDataAccessException(
                        "Incident dashboard metrics could not be read.", null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Incident dashboard metrics could not be read.", exception);
        }
    }
}
