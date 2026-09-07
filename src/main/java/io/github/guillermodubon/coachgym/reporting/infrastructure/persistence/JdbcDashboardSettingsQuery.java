package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.application.DashboardSettings;
import io.github.guillermodubon.coachgym.reporting.application.DashboardSettingsQuery;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the authoritative dashboard business settings. */
@Repository
class JdbcDashboardSettingsQuery implements DashboardSettingsQuery {

    static final String SQL = """
            select membership_expiration_warning_days, default_currency
            from gym.gym_settings
            order by id
            limit 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcDashboardSettingsQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public DashboardSettings load() {
        try {
            DashboardSettings result = jdbcTemplate.queryForObject(
                    SQL, new MapSqlParameterSource(),
                    (rs, row) -> new DashboardSettings(
                            rs.getInt("membership_expiration_warning_days"),
                            rs.getString("default_currency")));
            if (result == null) {
                throw new DashboardDataAccessException(
                        "Dashboard settings could not be read.", null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Dashboard settings could not be read.", exception);
        }
    }
}
