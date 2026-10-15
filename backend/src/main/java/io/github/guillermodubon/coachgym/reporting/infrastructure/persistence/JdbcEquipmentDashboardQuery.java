package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.EquipmentDashboardQuery;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for current equipment status metrics. */
@Repository
class JdbcEquipmentDashboardQuery implements EquipmentDashboardQuery {

    static final String SQL = """
            select
                count(*) filter (where status = 'AVAILABLE') as available_count,
                count(*) filter (where status = 'MAINTENANCE') as maintenance_count,
                count(*) filter (where status = 'OUT_OF_SERVICE') as out_of_service_count
            from gym.equipment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcEquipmentDashboardQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public EquipmentDashboardDetails summarize() {
        try {
            EquipmentDashboardDetails result = jdbcTemplate.queryForObject(
                    SQL, new MapSqlParameterSource(),
                    (rs, row) -> new EquipmentDashboardDetails(
                            rs.getLong("available_count"),
                            rs.getLong("maintenance_count"),
                            rs.getLong("out_of_service_count")));
            if (result == null) {
                throw new DashboardDataAccessException(
                        "Equipment dashboard metrics could not be read.", null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Equipment dashboard metrics could not be read.", exception);
        }
    }
}
