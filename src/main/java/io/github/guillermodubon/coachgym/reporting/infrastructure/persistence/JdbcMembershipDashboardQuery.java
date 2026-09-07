package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardPeriod;
import io.github.guillermodubon.coachgym.reporting.application.MembershipDashboardQuery;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL aggregate reader for current membership dashboard metrics. */
@Repository
class JdbcMembershipDashboardQuery implements MembershipDashboardQuery {

    static final String SQL = """
            select
                count(*) filter (
                    where m.status = 'ACTIVE'
                ) as active_count,
                count(*) filter (
                    where m.status = 'FROZEN'
                ) as frozen_count,
                count(*) filter (
                    where m.status = 'ACTIVE'
                      and current_period.effective_ends_on
                          between :operationalDate and :expirationUntil
                ) as expiring_soon_count
            from gym.memberships m
            left join lateral (
                select mp.effective_ends_on
                from gym.membership_periods mp
                where mp.membership_id = m.id
                order by mp.period_number desc
                limit 1
            ) current_period on true
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcMembershipDashboardQuery(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "Named parameter JDBC template is required.");
    }

    @Override
    public MembershipDashboardDetails summarize(
            DashboardPeriod period,
            int expirationWarningDays) {
        Objects.requireNonNull(period, "Dashboard period is required.");
        if (expirationWarningDays < 0 || expirationWarningDays > 90) {
            throw new ReportingValidationException(
                    "Membership expiration warning days must be between 0 and 90.");
        }

        LocalDate operationalDate = period.operationalDate();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("operationalDate", operationalDate)
                .addValue(
                        "expirationUntil",
                        operationalDate.plusDays(expirationWarningDays));

        try {
            MembershipDashboardDetails result = jdbcTemplate.queryForObject(
                    SQL,
                    parameters,
                    (resultSet, rowNumber) -> new MembershipDashboardDetails(
                            resultSet.getLong("active_count"),
                            resultSet.getLong("frozen_count"),
                            resultSet.getLong("expiring_soon_count")));

            if (result == null) {
                throw new DashboardDataAccessException(
                        "Membership dashboard metrics could not be read.",
                        null);
            }
            return result;
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Membership dashboard metrics could not be read.",
                    exception);
        }
    }
}
