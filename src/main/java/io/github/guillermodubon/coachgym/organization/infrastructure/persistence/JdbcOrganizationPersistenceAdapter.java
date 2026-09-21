package io.github.guillermodubon.coachgym.organization.infrastructure.persistence;

import io.github.guillermodubon.coachgym.organization.OrganizationBranchLifecyclePolicy;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.organization.application.OrganizationDataAccessException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationNotFoundException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationQuery;
import io.github.guillermodubon.coachgym.organization.application.OrganizationStore;
import io.github.guillermodubon.coachgym.organization.application.OrganizationVersionConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for the single canonical organization. */
@Repository
class JdbcOrganizationPersistenceAdapter
        implements OrganizationStore, OrganizationQuery, OrganizationIdentityQuery {

    static final String SELECT = """
            select id, code, legal_name, brand_name, support_email, support_phone,
                   default_timezone, default_currency, status, created_at, updated_at, version
            from gym.organizations
            where is_canonical = true
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OrganizationBranchLifecyclePolicy lifecyclePolicy =
            new OrganizationBranchLifecyclePolicy();

    JdbcOrganizationPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationDetails> findCanonical() {
        try {
            return jdbcTemplate.query(SELECT, JdbcOrganizationPersistenceAdapter::mapDetails)
                    .stream()
                    .findFirst();
        } catch (DataAccessException exception) {
            throw new OrganizationDataAccessException(
                    "The canonical organization could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public OrganizationDetails update(UpdateOrganizationCommand command, Instant occurredAt) {
        Objects.requireNonNull(command, "Organization update command is required.");
        requireInstant(occurredAt);
        String sql = """
                update gym.organizations
                set legal_name = :legalName,
                    brand_name = :brandName,
                    support_email = :supportEmail,
                    support_phone = :supportPhone,
                    default_timezone = :defaultTimezone,
                    default_currency = :defaultCurrency,
                    updated_at = :occurredAt,
                    version = version + 1
                where is_canonical = true
                  and version = :expectedVersion
                returning id, code, legal_name, brand_name, support_email, support_phone,
                          default_timezone, default_currency, status, created_at, updated_at, version
                """;
        try {
            List<OrganizationDetails> rows = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("legalName", command.legalName())
                            .addValue("brandName", command.brandName())
                            .addValue("supportEmail", command.supportEmail())
                            .addValue("supportPhone", command.supportPhone())
                            .addValue("defaultTimezone", command.defaultTimezone())
                            .addValue("defaultCurrency", command.defaultCurrency())
                            .addValue("occurredAt", offset(occurredAt))
                            .addValue("expectedVersion", command.expectedVersion()),
                    JdbcOrganizationPersistenceAdapter::mapDetails);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            return classifyMissingUpdate(command.expectedVersion());
        } catch (OrganizationVersionConflictException | OrganizationNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new OrganizationDataAccessException(
                    "The organization could not be updated.", exception);
        }
    }

    @Override
    @Transactional
    public OrganizationDetails changeStatus(
            OrganizationStatus requestedStatus,
            long expectedVersion,
            Instant occurredAt) {
        Objects.requireNonNull(requestedStatus, "Requested organization status is required.");
        requireInstant(occurredAt);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected organization version must not be negative.");
        }
        OrganizationDetails current = currentOrThrow();
        lifecyclePolicy.requireOrganizationTransition(
                current.status(), requestedStatus, true);
        String sql = """
                update gym.organizations
                set status = :status,
                    updated_at = :occurredAt,
                    version = version + 1
                where is_canonical = true
                  and version = :expectedVersion
                returning id, code, legal_name, brand_name, support_email, support_phone,
                          default_timezone, default_currency, status, created_at, updated_at, version
                """;
        try {
            List<OrganizationDetails> rows = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("status", requestedStatus.name())
                            .addValue("occurredAt", offset(occurredAt))
                            .addValue("expectedVersion", expectedVersion),
                    JdbcOrganizationPersistenceAdapter::mapDetails);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            return classifyMissingStatusChange(expectedVersion);
        } catch (OrganizationVersionConflictException | OrganizationNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new OrganizationDataAccessException(
                    "The organization status could not be changed.", exception);
        }
    }

    private OrganizationDetails currentOrThrow() {
        return findCanonical().orElseThrow(OrganizationNotFoundException::new);
    }

    private OrganizationDetails classifyMissingUpdate(long expectedVersion) {
        OrganizationDetails current = currentOrThrow();
        if (current.version() != expectedVersion) {
            throw new OrganizationVersionConflictException();
        }
        throw new OrganizationDataAccessException(
                "The organization could not be updated.",
                new IllegalStateException("The canonical organization update was not applied."));
    }

    private OrganizationDetails classifyMissingStatusChange(long expectedVersion) {
        OrganizationDetails current = currentOrThrow();
        if (current.version() != expectedVersion) {
            throw new OrganizationVersionConflictException();
        }
        throw new OrganizationDataAccessException(
                "The organization status could not be changed.",
                new IllegalStateException("The canonical organization status change was not applied."));
    }

    private static OrganizationDetails mapDetails(ResultSet rs, int row) throws SQLException {
        return new OrganizationDetails(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("code"),
                rs.getString("legal_name"),
                rs.getString("brand_name"),
                rs.getString("support_email"),
                rs.getString("support_phone"),
                rs.getString("default_timezone"),
                rs.getString("default_currency"),
                OrganizationStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireInstant(Instant value) {
        Objects.requireNonNull(value, "Persistence timestamp is required.");
    }
}
