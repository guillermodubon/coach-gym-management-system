package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientReportingQuery;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.client.ClientReportingUnavailableException;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL reader for client home-branch and registration aggregates. */
@Repository
@Transactional(readOnly = true)
class JdbcClientReportingQuery implements ClientReportingQuery {

    private static final String BRANCH_FILTER = "and c.home_branch_id in (:branchIds)";

    static final String ORGANIZATION_SQL = sql("");
    static final String BRANCH_SQL = sql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcClientReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public ClientReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        Objects.requireNonNull(scope, "Client reporting scope is required.");
        Objects.requireNonNull(window, "Client reporting window is required.");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInclusive", utc(window.fromInclusiveInstant()))
                .addValue("toExclusive", utc(window.toExclusiveInstant()));
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }

        try {
            ClientReportingSummary result = jdbcTemplate.queryForObject(
                    scope.organizationWide() ? ORGANIZATION_SQL : BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new ClientReportingSummary(
                            resultSet.getLong("total_clients"),
                            resultSet.getLong("active_clients"),
                            resultSet.getLong("inactive_clients"),
                            resultSet.getLong("registered_in_range")));
            if (result == null) {
                throw new ClientReportingUnavailableException(null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new ClientReportingUnavailableException(exception);
        }
    }

    private static String sql(String branchFilter) {
        return """
                select count(*) as total_clients,
                       count(*) filter (where c.status = 'ACTIVE') as active_clients,
                       count(*) filter (where c.status = 'INACTIVE') as inactive_clients,
                       count(*) filter (
                           where c.created_at >= :fromInclusive
                             and c.created_at < :toExclusive
                       ) as registered_in_range
                from gym.clients c
                where true
                  %s
                """.formatted(branchFilter);
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
