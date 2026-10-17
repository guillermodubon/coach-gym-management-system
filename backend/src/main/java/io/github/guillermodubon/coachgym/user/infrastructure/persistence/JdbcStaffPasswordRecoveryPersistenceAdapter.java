package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.PasswordRecoveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryDraft;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryPersistence;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryRecord;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Parameterized PostgreSQL persistence for one-time password recovery. */
@Component
class JdbcStaffPasswordRecoveryPersistenceAdapter implements StaffPasswordRecoveryPersistence {

    private final JdbcClient jdbcClient;

    JdbcStaffPasswordRecoveryPersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public StaffPasswordRecoveryRecord create(StaffPasswordRecoveryDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Password recovery draft is required.");
        }
        try {
            jdbcClient.sql("""
                            insert into gym.staff_password_recovery_tokens (
                                id, user_id, token_fingerprint, token_scheme, status,
                                requested_at, expires_at, failed_attempt_count, version)
                            values (:id, :userId, :fingerprint, :scheme, 'PENDING',
                                    :requestedAt, :expiresAt, 0, 0)
                            """)
                    .param("id", draft.recoveryId())
                    .param("userId", draft.userId())
                    .param("fingerprint", draft.tokenFingerprint().value())
                    .param("scheme", draft.tokenFingerprint().schemeVersion())
                    .param("requestedAt", StaffIdentityJdbcSupport.databaseTime(draft.requestedAt()))
                    .param("expiresAt", StaffIdentityJdbcSupport.databaseTime(draft.expiresAt()))
                    .update();
            return findById(draft.recoveryId()).orElseThrow(() ->
                    new StaffIdentityDataAccessException("Password recovery could not be created."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffPasswordRecoveryRecord> findPendingByFingerprint(
            StaffTokenFingerprint fingerprint) {
        requireFingerprint(fingerprint);
        try {
            return jdbcClient.sql("""
                            select *
                              from gym.staff_password_recovery_tokens
                             where token_fingerprint = :fingerprint
                               and token_scheme = :scheme
                               and status = 'PENDING'
                            """)
                    .param("fingerprint", fingerprint.value())
                    .param("scheme", fingerprint.schemeVersion())
                    .query(this::mapRecovery)
                    .optional();
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public Optional<StaffPasswordRecoveryRecord> lockPendingByFingerprint(
            StaffTokenFingerprint fingerprint) {
        requireFingerprint(fingerprint);
        try {
            return jdbcClient.sql("""
                            select *
                              from gym.staff_password_recovery_tokens
                             where token_fingerprint = :fingerprint
                               and token_scheme = :scheme
                               and status = 'PENDING'
                             for update
                            """)
                    .param("fingerprint", fingerprint.value())
                    .param("scheme", fingerprint.schemeVersion())
                    .query(this::mapRecovery)
                    .optional();
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public void revokePendingForUser(UUID userId, Instant occurredAt) {
        if (userId == null || occurredAt == null) {
            throw new IllegalArgumentException("Password recovery revocation is incomplete.");
        }
        try {
            jdbcClient.sql("""
                            update gym.staff_password_recovery_tokens
                               set status = 'REVOKED',
                                   revoked_at = :occurredAt,
                                   version = version + 1
                             where user_id = :userId
                               and status = 'PENDING'
                            """)
                    .param("occurredAt", StaffIdentityJdbcSupport.databaseTime(occurredAt))
                    .param("userId", userId)
                    .update();
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public StaffPasswordRecoveryRecord transition(
            UUID recoveryId,
            PasswordRecoveryStatus target,
            Instant occurredAt,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (recoveryId == null || target == null || occurredAt == null) {
            throw new IllegalArgumentException("Password recovery transition is incomplete.");
        }
        PasswordRecoveryLifecyclePolicy.requireTransition(PasswordRecoveryStatus.PENDING, target);
        try {
            int updated = jdbcClient.sql("""
                            update gym.staff_password_recovery_tokens
                               set status = :target,
                                   used_at = case when :target = 'USED' then :occurredAt else null end,
                                   expired_at = case when :target = 'EXPIRED' then :occurredAt else null end,
                                   revoked_at = case when :target = 'REVOKED' then :occurredAt else null end,
                                   version = version + 1
                             where id = :id
                               and status = 'PENDING'
                               and version = :expectedVersion
                               and ((:target = 'USED' and expires_at > :occurredAt)
                                 or (:target = 'EXPIRED' and expires_at <= :occurredAt)
                                 or :target = 'REVOKED')
                            """)
                    .param("target", target.name())
                    .param("occurredAt", StaffIdentityJdbcSupport.databaseTime(occurredAt))
                    .param("id", recoveryId)
                    .param("expectedVersion", expectedVersion)
                    .update();
            if (updated != 1) {
                throw new StaffIdentityStateConflictException(
                        "Password recovery request is no longer pending or its version changed.");
            }
            return findById(recoveryId).orElseThrow(() ->
                    new StaffIdentityStateConflictException("Password recovery request is unavailable."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public StaffPasswordRecoveryRecord recordFailedAttempt(
            UUID recoveryId,
            Instant occurredAt,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (recoveryId == null || occurredAt == null) {
            throw new IllegalArgumentException("Password recovery attempt is incomplete.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.staff_password_recovery_tokens
                               set failed_attempt_count = failed_attempt_count + 1,
                                   status = case when failed_attempt_count = 4 then 'REVOKED' else status end,
                                   revoked_at = case when failed_attempt_count = 4 then :occurredAt else revoked_at end,
                                   version = version + 1
                             where id = :id
                               and status = 'PENDING'
                               and version = :expectedVersion
                               and failed_attempt_count < 5
                            """)
                    .param("occurredAt", StaffIdentityJdbcSupport.databaseTime(occurredAt))
                    .param("id", recoveryId)
                    .param("expectedVersion", expectedVersion)
                    .update();
            if (updated != 1) {
                throw new StaffIdentityStateConflictException(
                        "Password recovery request is no longer pending or its version changed.");
            }
            return findById(recoveryId).orElseThrow(() ->
                    new StaffIdentityStateConflictException("Password recovery request is unavailable."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    private Optional<StaffPasswordRecoveryRecord> findById(UUID recoveryId) {
        return jdbcClient.sql("""
                        select * from gym.staff_password_recovery_tokens where id = :id
                        """)
                .param("id", recoveryId)
                .query(this::mapRecovery)
                .optional();
    }

    private StaffPasswordRecoveryRecord mapRecovery(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new StaffPasswordRecoveryRecord(
                StaffIdentityJdbcSupport.uuid(resultSet, "id"),
                StaffIdentityJdbcSupport.uuid(resultSet, "user_id"),
                new StaffTokenFingerprint(
                        resultSet.getString("token_fingerprint").stripTrailing(),
                        resultSet.getString("token_scheme")),
                PasswordRecoveryStatus.valueOf(resultSet.getString("status")),
                StaffIdentityJdbcSupport.instant(resultSet, "requested_at"),
                StaffIdentityJdbcSupport.instant(resultSet, "expires_at"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "used_at"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "expired_at"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "revoked_at"),
                resultSet.getInt("failed_attempt_count"),
                resultSet.getLong("version"));
    }

    private static void requireFingerprint(StaffTokenFingerprint fingerprint) {
        if (fingerprint == null || !StaffTokenPurpose.PASSWORD_RECOVERY.schemeVersion()
                .equals(fingerprint.schemeVersion())) {
            throw new IllegalArgumentException("Password recovery fingerprint is invalid.");
        }
    }

    private static void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected version is invalid.");
        }
    }
}
