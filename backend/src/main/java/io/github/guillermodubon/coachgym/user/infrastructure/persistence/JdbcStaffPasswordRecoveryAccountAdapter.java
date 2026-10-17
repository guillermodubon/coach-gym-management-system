package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityVersionConflictException;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryAccount;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryAccountStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Parameterized account locking and credential update for password recovery. */
@Repository
class JdbcStaffPasswordRecoveryAccountAdapter implements StaffPasswordRecoveryAccountStore {

    private static final String ACCOUNT_COLUMNS = """
            select id, lower(btrim(email)) as normalized_email,
                   first_name, last_name, password_hash, status, security_version
              from gym.users
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffPasswordRecoveryAccountAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Optional<StaffPasswordRecoveryAccount> lockByNormalizedEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Recovery account identity is required.");
        }
        try {
            return jdbcTemplate.query(ACCOUNT_COLUMNS + """
                    where lower(btrim(email)) = :email
                    for update
                    """, new MapSqlParameterSource("email", normalizedEmail), this::mapAccount)
                    .stream().findFirst();
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Staff identity data could not be accessed.");
        }
    }

    @Override
    @Transactional
    public Optional<StaffPasswordRecoveryAccount> lockById(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Recovery account identity is required.");
        }
        try {
            return jdbcTemplate.query(ACCOUNT_COLUMNS + """
                    where id = :userId
                    for update
                    """, new MapSqlParameterSource("userId", userId), this::mapAccount)
                    .stream().findFirst();
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Staff identity data could not be accessed.");
        }
    }

    @Override
    @Transactional
    public void updatePassword(
            UUID userId,
            long expectedSecurityVersion,
            String encodedPassword,
            Instant occurredAt) {
        if (userId == null || expectedSecurityVersion < 0
                || encodedPassword == null || encodedPassword.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("Password recovery update is incomplete.");
        }
        try {
            int updated = jdbcTemplate.update("""
                    update gym.users
                       set password_hash = :passwordHash,
                           password_change_required = false,
                           security_version = security_version + 1,
                           version = version + 1
                     where id = :userId
                       and status = 'ACTIVE'
                       and security_version = :expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("passwordHash", encodedPassword)
                    .addValue("userId", userId)
                    .addValue("expectedVersion", expectedSecurityVersion));
            if (updated != 1) {
                throw new StaffIdentityVersionConflictException(userId);
            }
        } catch (StaffIdentityVersionConflictException | StaffIdentityStateConflictException conflict) {
            throw conflict;
        } catch (DataAccessException failure) {
            throw new StaffIdentityDataAccessException("Staff password could not be changed.");
        }
    }

    private StaffPasswordRecoveryAccount mapAccount(
            ResultSet resultSet, int rowNum) throws SQLException {
        return new StaffPasswordRecoveryAccount(
                StaffIdentityJdbcSupport.uuid(resultSet, "id"),
                resultSet.getString("normalized_email"),
                resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
                resultSet.getString("password_hash"),
                StaffAccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("security_version"));
    }
}
