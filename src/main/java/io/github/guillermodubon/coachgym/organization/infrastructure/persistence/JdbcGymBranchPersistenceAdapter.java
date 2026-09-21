package io.github.guillermodubon.coachgym.organization.infrastructure.persistence;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
import io.github.guillermodubon.coachgym.organization.OrganizationBranchLifecyclePolicy;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.application.GymBranchCodeConflictException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchDataAccessException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchNotFoundException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchPage;
import io.github.guillermodubon.coachgym.organization.application.GymBranchQuery;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSearchQuery;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortDirection;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortField;
import io.github.guillermodubon.coachgym.organization.application.GymBranchStore;
import io.github.guillermodubon.coachgym.organization.application.GymBranchVersionConflictException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for branches belonging to the canonical organization. */
@Repository
class JdbcGymBranchPersistenceAdapter implements GymBranchStore, GymBranchQuery {

    static final String SELECT = """
            select b.id, b.organization_id, b.code, b.name,
                   b.address_line1, b.address_line2, b.city,
                   b.state_or_department, b.postal_code, b.country_code,
                   b.phone, b.email, b.timezone, b.status,
                   b.is_initial_branch, b.created_at, b.updated_at, b.version
            from gym.gym_branches b
            where b.organization_id = (
                select id from gym.organizations where is_canonical = true
            )
            """;

    private static final Map<GymBranchSortField, String> SORT_COLUMNS = Map.of(
            GymBranchSortField.CODE, "b.code",
            GymBranchSortField.NAME, "b.name",
            GymBranchSortField.CITY, "b.city",
            GymBranchSortField.CREATED_AT, "b.created_at",
            GymBranchSortField.UPDATED_AT, "b.updated_at");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OrganizationBranchLifecyclePolicy lifecyclePolicy =
            new OrganizationBranchLifecyclePolicy();

    JdbcGymBranchPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public GymBranchDetails create(CreateGymBranchCommand command, Instant occurredAt) {
        Objects.requireNonNull(command, "Branch creation command is required.");
        requireInstant(occurredAt);
        String sql = """
                insert into gym.gym_branches (
                    id, organization_id, code, name, address_line1, address_line2,
                    city, state_or_department, postal_code, country_code, phone, email,
                    timezone, status, is_initial_branch, created_at, updated_at, version)
                select :id, o.id, :code, :name, :addressLine1, :addressLine2,
                       :city, :stateOrDepartment, :postalCode, :countryCode,
                       :phone, :email, :timezone, 'ACTIVE', false,
                       :occurredAt, :occurredAt, 0
                from gym.organizations o
                where o.is_canonical = true
                returning id, organization_id, code, name, address_line1, address_line2,
                          city, state_or_department, postal_code, country_code,
                          phone, email, timezone, status, is_initial_branch,
                          created_at, updated_at, version
                """;
        try {
            List<GymBranchDetails> rows = jdbcTemplate.query(
                    sql,
                    createParameters(command, occurredAt),
                    JdbcGymBranchPersistenceAdapter::mapDetails);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            throw new OrganizationNotFoundException();
        } catch (OrganizationNotFoundException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new GymBranchCodeConflictException();
            }
            throw new GymBranchDataAccessException(
                    "The gym branch could not be created.", exception);
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch could not be created.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GymBranchDetails> findById(UUID id) {
        requireIdentifier(id);
        return find(SELECT + " and b.id = :id", new MapSqlParameterSource("id", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GymBranchSummary> findActiveById(UUID id) {
        requireIdentifier(id);
        return find(
                        SELECT + " and b.id = :id and b.status = 'ACTIVE'",
                        new MapSqlParameterSource("id", id))
                .map(GymBranchSummary::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GymBranchDetails> findByCode(String code) {
        String normalized = normalizeCode(code);
        return find(
                SELECT + " and b.code = :code",
                new MapSqlParameterSource("code", normalized));
    }

    @Override
    @Transactional(readOnly = true)
    public GymBranchPage findAll(GymBranchSearchQuery query) {
        Objects.requireNonNull(query, "Branch search query is required.");
        MapSqlParameterSource parameters = queryParameters(query);
        String dataSql = SELECT + filters() + orderBy(query)
                + " limit :limit offset :offset";
        try {
            Long total = jdbcTemplate.queryForObject(
                    "select count(*) from gym.gym_branches b "
                            + "where b.organization_id = ("
                            + "select id from gym.organizations where is_canonical = true"
                            + ")" + filters(),
                    parameters,
                    Long.class);
            List<GymBranchDetails> items = jdbcTemplate.query(
                    dataSql, parameters, JdbcGymBranchPersistenceAdapter::mapDetails);
            long safeTotal = total == null ? 0L : total;
            int totalPages = safeTotal == 0L
                    ? 0
                    : (int) ((safeTotal + query.size() - 1) / query.size());
            return new GymBranchPage(
                    items, query.page(), query.size(), safeTotal, totalPages);
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch catalog could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public GymBranchDetails update(
            UUID id,
            UpdateGymBranchCommand command,
            Instant occurredAt) {
        requireIdentifier(id);
        Objects.requireNonNull(command, "Branch update command is required.");
        requireInstant(occurredAt);
        String sql = """
                update gym.gym_branches b
                set name = :name,
                    address_line1 = :addressLine1,
                    address_line2 = :addressLine2,
                    city = :city,
                    state_or_department = :stateOrDepartment,
                    postal_code = :postalCode,
                    country_code = :countryCode,
                    phone = :phone,
                    email = :email,
                    timezone = :timezone,
                    updated_at = :occurredAt,
                    version = b.version + 1
                where b.id = :id
                  and b.organization_id = (
                      select id from gym.organizations where is_canonical = true
                  )
                  and b.version = :expectedVersion
                returning b.id, b.organization_id, b.code, b.name,
                          b.address_line1, b.address_line2, b.city,
                          b.state_or_department, b.postal_code, b.country_code,
                          b.phone, b.email, b.timezone, b.status,
                          b.is_initial_branch, b.created_at, b.updated_at, b.version
                """;
        try {
            List<GymBranchDetails> rows = jdbcTemplate.query(
                    sql,
                    updateParameters(id, command, occurredAt),
                    JdbcGymBranchPersistenceAdapter::mapDetails);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            return classifyMissingUpdate(id, command.expectedVersion());
        } catch (GymBranchVersionConflictException | GymBranchNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch could not be updated.", exception);
        }
    }

    @Override
    @Transactional
    public GymBranchDetails changeStatus(
            UUID id,
            ChangeGymBranchStatusCommand command,
            Instant occurredAt) {
        requireIdentifier(id);
        Objects.requireNonNull(command, "Branch status command is required.");
        requireInstant(occurredAt);
        GymBranchDetails current = findById(id)
                .orElseThrow(() -> new GymBranchNotFoundException(id));
        boolean anotherActiveBranchExists = hasAnotherActiveBranch(id);
        lifecyclePolicy.requireBranchTransition(
                current.status(),
                command.requestedStatus(),
                current.isInitialBranch(),
                anotherActiveBranchExists);
        String sql = """
                update gym.gym_branches b
                set status = :status,
                    updated_at = :occurredAt,
                    version = b.version + 1
                where b.id = :id
                  and b.organization_id = (
                      select id from gym.organizations where is_canonical = true
                  )
                  and b.version = :expectedVersion
                returning b.id, b.organization_id, b.code, b.name,
                          b.address_line1, b.address_line2, b.city,
                          b.state_or_department, b.postal_code, b.country_code,
                          b.phone, b.email, b.timezone, b.status,
                          b.is_initial_branch, b.created_at, b.updated_at, b.version
                """;
        try {
            List<GymBranchDetails> rows = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("status", command.requestedStatus().name())
                            .addValue("occurredAt", offset(occurredAt))
                            .addValue("expectedVersion", command.expectedVersion()),
                    JdbcGymBranchPersistenceAdapter::mapDetails);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            return classifyMissingUpdate(id, command.expectedVersion());
        } catch (GymBranchVersionConflictException | GymBranchNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch status could not be changed.", exception);
        }
    }

    static String orderBy(GymBranchSearchQuery query) {
        String column = SORT_COLUMNS.get(query.sortField());
        if (column == null) {
            throw new IllegalArgumentException("Unsupported gym branch sort field.");
        }
        String direction = query.direction() == GymBranchSortDirection.DESC
                ? "desc" : "asc";
        return " order by " + column + " " + direction + ", b.id asc";
    }

    private Optional<GymBranchDetails> find(
            String sql,
            MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.query(sql, parameters, JdbcGymBranchPersistenceAdapter::mapDetails)
                    .stream()
                    .findFirst();
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch could not be read.", exception);
        }
    }

    private boolean hasAnotherActiveBranch(UUID id) {
        try {
            Boolean result = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1
                        from gym.gym_branches b
                        where b.organization_id = (
                            select id from gym.organizations where is_canonical = true
                        )
                          and b.status = 'ACTIVE'
                          and b.id <> :id
                    )
                    """, new MapSqlParameterSource("id", id), Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException exception) {
            throw new GymBranchDataAccessException(
                    "The gym branch lifecycle state could not be read.", exception);
        }
    }

    private GymBranchDetails classifyMissingUpdate(UUID id, long expectedVersion) {
        GymBranchDetails current = findById(id)
                .orElseThrow(() -> new GymBranchNotFoundException(id));
        if (current.version() != expectedVersion) {
            throw new GymBranchVersionConflictException();
        }
        throw new GymBranchDataAccessException(
                "The gym branch could not be updated.",
                new IllegalStateException("The branch update was not applied."));
    }

    private static String filters() {
        return """
                and (cast(:status as varchar) is null
                     or b.status = cast(:status as varchar))
                and (cast(:search as varchar) is null
                     or lower(b.code) like cast(:searchPattern as varchar) escape '\\'
                     or lower(b.name) like cast(:searchPattern as varchar) escape '\\'
                     or lower(coalesce(b.city, '')) like cast(:searchPattern as varchar) escape '\\')
                and (cast(:city as varchar) is null
                     or lower(coalesce(b.city, '')) = lower(cast(:city as varchar)))
                and (cast(:countryCode as varchar) is null
                     or b.country_code = cast(:countryCode as varchar))
                """;
    }

    private static MapSqlParameterSource queryParameters(GymBranchSearchQuery query) {
        String pattern = query.search() == null
                ? null
                : "%" + escapeLike(query.search().toLowerCase(Locale.ROOT)) + "%";
        return new MapSqlParameterSource()
                .addValue("status", query.status() == null ? null : query.status().name())
                .addValue("search", query.search())
                .addValue("searchPattern", pattern)
                .addValue("city", query.city())
                .addValue("countryCode", query.countryCode())
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
    }

    private static MapSqlParameterSource createParameters(
            CreateGymBranchCommand command,
            Instant occurredAt) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("code", command.code())
                .addValue("name", command.name())
                .addValue("addressLine1", command.addressLine1())
                .addValue("addressLine2", command.addressLine2())
                .addValue("city", command.city())
                .addValue("stateOrDepartment", command.stateOrDepartment())
                .addValue("postalCode", command.postalCode())
                .addValue("countryCode", command.countryCode())
                .addValue("phone", command.phone())
                .addValue("email", command.email())
                .addValue("timezone", command.timezone())
                .addValue("occurredAt", offset(occurredAt));
    }

    private static MapSqlParameterSource updateParameters(
            UUID id,
            UpdateGymBranchCommand command,
            Instant occurredAt) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", command.name())
                .addValue("addressLine1", command.addressLine1())
                .addValue("addressLine2", command.addressLine2())
                .addValue("city", command.city())
                .addValue("stateOrDepartment", command.stateOrDepartment())
                .addValue("postalCode", command.postalCode())
                .addValue("countryCode", command.countryCode())
                .addValue("phone", command.phone())
                .addValue("email", command.email())
                .addValue("timezone", command.timezone())
                .addValue("occurredAt", offset(occurredAt))
                .addValue("expectedVersion", command.expectedVersion());
    }

    private static GymBranchDetails mapDetails(ResultSet rs, int row) throws SQLException {
        return new GymBranchDetails(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("address_line1"),
                rs.getString("address_line2"),
                rs.getString("city"),
                rs.getString("state_or_department"),
                rs.getString("postal_code"),
                rs.getString("country_code"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("timezone"),
                GymBranchStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_initial_branch"),
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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Branch code is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Branch code is required.");
        }
        return normalized;
    }

    private static void requireIdentifier(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Branch id is required.");
        }
    }

    private static void requireInstant(Instant value) {
        Objects.requireNonNull(value, "Persistence timestamp is required.");
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
