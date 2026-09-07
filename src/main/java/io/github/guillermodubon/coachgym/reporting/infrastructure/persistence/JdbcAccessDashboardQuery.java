package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.AccessDashboardQuery;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for access decisions during the operational day. */
@Repository
class JdbcAccessDashboardQuery implements AccessDashboardQuery {

    static final String SQL = """
        select
            count(*) filter (
                where ar.decision = 'GRANTED'
            ) as allowed_count,
            count(*) filter (
                where ar.decision = 'DENIED'
            ) as denied_count
        from gym.access_records ar
        where ar.occurred_at >= :dayFromInclusive
          and ar.occurred_at < :dayUntilExclusive
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAccessDashboardQuery(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "Named parameter JDBC template is required.");
    }

    @Override
    public AccessDashboardDetails summarizeToday(DashboardPeriod period) {
        Objects.requireNonNull(period, "Dashboard period is required.");

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(
                        "dayFromInclusive",
                        OffsetDateTime.ofInstant(
                                period.operationalDayFromInclusive(),
                                ZoneOffset.UTC))
                .addValue(
                        "dayUntilExclusive",
                        OffsetDateTime.ofInstant(
                                period.operationalDayUntilExclusive(),
                                ZoneOffset.UTC));

        try {
            AccessDashboardDetails result = jdbcTemplate.queryForObject(
                    SQL,
                    parameters,
                    (resultSet, rowNumber) -> new AccessDashboardDetails(
                            resultSet.getLong("allowed_count"),
                            resultSet.getLong("denied_count")));

            if (result == null) {
                throw new DashboardDataAccessException(
                        "Access dashboard metrics could not be read.",
                        null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Access dashboard metrics could not be read.",
                    exception);
        }
    }
}
