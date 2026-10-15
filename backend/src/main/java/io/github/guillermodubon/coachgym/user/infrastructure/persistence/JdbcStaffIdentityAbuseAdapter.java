package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.application.StaffIdentityAbuseBucket;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAbuseStore;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic cross-instance rate counters; short-lived subjects are never logged or metered. */
@Repository
class JdbcStaffIdentityAbuseAdapter implements StaffIdentityAbuseStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffIdentityAbuseAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt) {
        requireInput(bucket, subject, maximum, window, occurredAt);
        try {
            pruneExpired(occurredAt);
            return increment(bucket, subject, maximum, window, occurredAt);
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Identity abuse controls are unavailable.");
        }
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isAllowed(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Instant occurredAt) {
        if (bucket == null || subject == null || subject.isBlank()
                || subject.length() > 254 || maximum < 1 || occurredAt == null) {
            throw new IllegalArgumentException("Identity abuse check is incomplete.");
        }
        try {
            Boolean allowed = jdbcTemplate.queryForObject("""
                    select coalesce(
                        (select expires_at <= :now or attempt_count < :maximum
                           from gym.staff_identity_abuse_windows
                          where bucket_type = :bucket
                            and subject_key = :subject),
                        true)
                    """, parameters(bucket, subject, maximum, occurredAt), Boolean.class);
            return Boolean.TRUE.equals(allowed);
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Identity abuse controls are unavailable.");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt) {
        requireInput(bucket, subject, maximum, window, occurredAt);
        try {
            pruneExpired(occurredAt);
            increment(bucket, subject, maximum, window, occurredAt);
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Identity abuse controls are unavailable.");
        }
    }

    private boolean increment(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt) {
        Boolean allowed = jdbcTemplate.queryForObject("""
                insert into gym.staff_identity_abuse_windows (
                    bucket_type, subject_key, attempt_count, window_started_at, expires_at)
                values (:bucket, :subject, 1, :now, :expiresAt)
                on conflict (bucket_type, subject_key) do update
                   set attempt_count = case
                           when gym.staff_identity_abuse_windows.expires_at <= :now then 1
                           else least(gym.staff_identity_abuse_windows.attempt_count + 1, :cap)
                       end,
                       window_started_at = case
                           when gym.staff_identity_abuse_windows.expires_at <= :now then :now
                           else gym.staff_identity_abuse_windows.window_started_at
                       end,
                       expires_at = case
                           when gym.staff_identity_abuse_windows.expires_at <= :now then :expiresAt
                           else gym.staff_identity_abuse_windows.expires_at
                       end
                returning attempt_count <= :maximum
                """, parameters(bucket, subject, maximum, occurredAt)
                .addValue("expiresAt", Timestamp.from(occurredAt.plus(window)))
                .addValue("cap", maximum + 1), Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }

    private void pruneExpired(Instant now) {
        jdbcTemplate.update("""
                delete from gym.staff_identity_abuse_windows
                 where expires_at <= :now
                """, new MapSqlParameterSource("now", Timestamp.from(now)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pruneExpiredWindows(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Identity abuse cleanup time is required.");
        }
        try {
            pruneExpired(now);
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Identity abuse controls are unavailable.");
        }
    }

    private static MapSqlParameterSource parameters(
            StaffIdentityAbuseBucket bucket, String subject, int maximum, Instant now) {
        return new MapSqlParameterSource()
                .addValue("bucket", bucket.name())
                .addValue("subject", subject)
                .addValue("maximum", maximum)
                .addValue("now", Timestamp.from(now));
    }

    private static void requireInput(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt) {
        if (bucket == null || subject == null || subject.isBlank() || subject.length() > 254
                || maximum < 1 || maximum > 100_000
                || window == null || window.isNegative() || window.isZero() || occurredAt == null) {
            throw new IllegalArgumentException("Identity abuse counter input is incomplete.");
        }
    }
}
