package io.github.guillermodubon.coachgym.configuration.infrastructure.persistence;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyStore;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyVersionConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for the singleton, PostgreSQL-backed policy setting. */
@Repository
class JdbcAccessPaymentPolicyAdapter
        implements AccessPaymentPolicyQuery, AccessPaymentPolicyStore {

    static final String FIND_CURRENT_SQL = """
            select require_confirmed_payment_for_access,
                   version, updated_at, updated_by_user_id
            from gym.gym_settings
            where id = 1
            """;

    static final String UPDATE_SQL = """
            update gym.gym_settings
            set require_confirmed_payment_for_access = :requireConfirmedPayment,
                updated_by_user_id = :updatedByUserId,
                updated_at = :updatedAt,
                version = version + 1
            where id = 1
              and version = :expectedVersion
            returning require_confirmed_payment_for_access,
                      version, updated_at, updated_by_user_id
            """;

    static final String EXISTS_SQL = """
            select exists(
                select 1
                from gym.gym_settings
                where id = 1
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAccessPaymentPolicyAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessPaymentPolicyDetails findCurrent() {
        try {
            AccessPaymentPolicyDetails details = jdbcTemplate.queryForObject(
                    FIND_CURRENT_SQL,
                    new MapSqlParameterSource(),
                    JdbcAccessPaymentPolicyAdapter::mapDetails);
            if (details == null) {
                throw new AccessPaymentPolicyDataAccessException(
                        "Access payment policy could not be read.", null);
            }
            return details;
        } catch (AccessPaymentPolicyDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new AccessPaymentPolicyDataAccessException(
                    "Access payment policy could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public AccessPaymentPolicyDetails update(
            AccessPaymentPolicy policy,
            long expectedVersion,
            UUID actorId,
            Instant occurredAt) {

        validateUpdate(policy, expectedVersion, actorId, occurredAt);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("requireConfirmedPayment",
                        policy.requireConfirmedPaymentForAccess())
                .addValue("updatedByUserId", actorId)
                .addValue("updatedAt", offset(occurredAt))
                .addValue("expectedVersion", expectedVersion);

        try {
            List<AccessPaymentPolicyDetails> updated = jdbcTemplate.query(
                    UPDATE_SQL,
                    parameters,
                    JdbcAccessPaymentPolicyAdapter::mapDetails);
            if (!updated.isEmpty()) {
                return updated.get(0);
            }

            boolean rowExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    EXISTS_SQL,
                    new MapSqlParameterSource(),
                    Boolean.class));
            if (rowExists) {
                throw new AccessPaymentPolicyVersionConflictException();
            }
            throw new AccessPaymentPolicyDataAccessException(
                    "Access payment policy could not be updated.", null);
        } catch (AccessPaymentPolicyVersionConflictException
                | AccessPaymentPolicyDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new AccessPaymentPolicyDataAccessException(
                    "Access payment policy could not be updated.", exception);
        }
    }

    private static void validateUpdate(
            AccessPaymentPolicy policy,
            long expectedVersion,
            UUID actorId,
            Instant occurredAt) {
        if (policy == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Access payment policy must be provided.");
        }
        if (expectedVersion < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "Expected access payment policy version must not be negative.");
        }
        if (actorId == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Policy update actor must be provided.");
        }
        if (occurredAt == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Policy update timestamp must be provided.");
        }
    }

    private static AccessPaymentPolicyDetails mapDetails(
            ResultSet resultSet,
            int row) throws SQLException {
        OffsetDateTime updatedAt = resultSet.getObject(
                "updated_at", OffsetDateTime.class);
        return new AccessPaymentPolicyDetails(
                resultSet.getBoolean("require_confirmed_payment_for_access"),
                resultSet.getLong("version"),
                updatedAt == null ? null : updatedAt.toInstant(),
                resultSet.getObject("updated_by_user_id", UUID.class));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
