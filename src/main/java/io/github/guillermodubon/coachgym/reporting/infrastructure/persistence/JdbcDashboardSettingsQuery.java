package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.application.DashboardSettings;
import io.github.guillermodubon.coachgym.reporting.application.DashboardSettingsQuery;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the authoritative dashboard business settings. */
@Repository
class JdbcDashboardSettingsQuery implements DashboardSettingsQuery {

    static final String SQL = """
            select membership_expiration_warning_days
            from gym.gym_settings
            order by id
            limit 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OrganizationIdentityQuery organizationQuery;

    JdbcDashboardSettingsQuery(
            NamedParameterJdbcTemplate jdbcTemplate,
            OrganizationIdentityQuery organizationQuery) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.organizationQuery = Objects.requireNonNull(organizationQuery);
    }

    @Override
    public DashboardSettings load() {
        try {
            Integer warningDays = jdbcTemplate.queryForObject(
                    SQL, new MapSqlParameterSource(),
                    (rs, row) -> rs.getInt("membership_expiration_warning_days"));
            String currency = organizationQuery.findCanonical()
                    .map(organization -> organization.defaultCurrency())
                    .orElseThrow(() -> new DashboardDataAccessException(
                            "Canonical organization could not be read.", null));
            if (warningDays == null) {
                throw new DashboardDataAccessException(
                        "Dashboard settings could not be read.", null);
            }
            return new DashboardSettings(warningDays, currency);
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Dashboard settings could not be read.", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof DashboardDataAccessException dashboardException) {
                throw dashboardException;
            }
            throw new DashboardDataAccessException(
                    "Dashboard settings could not be read.", exception);
        }
    }
}
