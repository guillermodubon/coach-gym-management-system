package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.application.AdminReauthenticationAttemptStore;
import io.github.guillermodubon.coachgym.auth.application.CurrentPasswordVerificationService;
import io.github.guillermodubon.coachgym.auth.application.CurrentPasswordVerificationUnavailableException;
import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL-backed throttle; an advisory transaction lock serializes checks per actor. */
@Repository
class JdbcAdminReauthenticationAttemptAdapter implements AdminReauthenticationAttemptStore {

    private final JdbcClient jdbcClient;
    private final StaffIdentityAbuseLimits abuseLimits;

    JdbcAdminReauthenticationAttemptAdapter(
            JdbcClient jdbcClient,
            StaffIdentityAbuseLimits abuseLimits) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.abuseLimits = Objects.requireNonNull(abuseLimits);
    }

    @Override
    public boolean beginCheck(UUID actorId, Instant now) {
        requireInput(actorId, now);
        try {
            jdbcClient.sql("""
                            select pg_advisory_xact_lock(
                                hashtextextended('staff-admin-reauth:' || cast(:actorId as text), 0))
                            """)
                    .param("actorId", actorId)
                    .query((resultSet, rowNum) -> Boolean.TRUE)
                    .single();
            jdbcClient.sql("""
                            delete from gym.staff_admin_reauthentication_failures
                             where user_id = :actorId
                               and attempted_at < :cutoff
                            """)
                    .param("actorId", actorId)
                    .param("cutoff", Timestamp.from(now.minus(
                            abuseLimits.adminReauthenticationFailureWindow())))
                    .update();
            Integer count = jdbcClient.sql("""
                            select count(*)
                              from gym.staff_admin_reauthentication_failures
                             where user_id = :actorId
                               and attempted_at >= :cutoff
                            """)
                    .param("actorId", actorId)
                    .param("cutoff", Timestamp.from(now.minus(
                            abuseLimits.adminReauthenticationFailureWindow())))
                    .query(Integer.class)
                    .single();
            return count < abuseLimits.maxAdminReauthenticationFailuresPerActorPer15Minutes();
        } catch (DataAccessException exception) {
            throw new CurrentPasswordVerificationUnavailableException();
        }
    }

    @Override
    public void recordFailure(UUID actorId, Instant attemptedAt) {
        requireInput(actorId, attemptedAt);
        try {
            jdbcClient.sql("""
                            insert into gym.staff_admin_reauthentication_failures (id, user_id, attempted_at)
                            values (:id, :actorId, :attemptedAt)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("actorId", actorId)
                    .param("attemptedAt", Timestamp.from(attemptedAt))
                    .update();
        } catch (DataAccessException exception) {
            throw new CurrentPasswordVerificationUnavailableException();
        }
    }

    @Override
    public void clearFailures(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("Actor identity is required.");
        }
        try {
            jdbcClient.sql("delete from gym.staff_admin_reauthentication_failures where user_id = :actorId")
                    .param("actorId", actorId)
                    .update();
        } catch (DataAccessException exception) {
            throw new CurrentPasswordVerificationUnavailableException();
        }
    }

    private static void requireInput(UUID actorId, Instant instant) {
        if (actorId == null || instant == null) {
            throw new IllegalArgumentException("Reauthentication attempt metadata is required.");
        }
    }
}
