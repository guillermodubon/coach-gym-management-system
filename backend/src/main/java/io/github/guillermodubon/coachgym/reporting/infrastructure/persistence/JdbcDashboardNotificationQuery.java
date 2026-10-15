package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardNotificationQuery;
import java.util.UUID;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL reader for the authenticated recipient's unread notifications. */
@Repository
class JdbcDashboardNotificationQuery implements DashboardNotificationQuery {

    static final String SQL = """
            select count(*)
            from gym.notifications
            where recipient_user_id = :recipientUserId
              and read_at is null
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcDashboardNotificationQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public DashboardNotificationDetails summarize(UUID recipientUserId) {
        Objects.requireNonNull(recipientUserId, "Recipient user id is required.");
        try {
            Long unread = jdbcTemplate.queryForObject(
                    SQL,
                    new MapSqlParameterSource(
                            "recipientUserId", recipientUserId),
                    Long.class);
            if (unread == null) {
                throw new DashboardDataAccessException(
                        "Notification dashboard metrics could not be read.", null);
            }
            return new DashboardNotificationDetails(unread);
        } catch (DataAccessException exception) {
            throw new DashboardDataAccessException(
                    "Notification dashboard metrics could not be read.", exception);
        }
    }
}
