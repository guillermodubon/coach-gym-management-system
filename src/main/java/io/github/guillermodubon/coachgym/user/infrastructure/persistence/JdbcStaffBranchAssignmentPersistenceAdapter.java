package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.AssignStaffToBranchCommand;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.EndStaffBranchAssignmentCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentPage;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentQuery;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffAssignmentAuthorizationQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentAdminQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentDuplicateException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchPage;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchResult;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortField;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentStore;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentVersionConflictException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeStore;
import io.github.guillermodubon.coachgym.user.application.StaffScopeVersionConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC persistence boundary for staff scopes, append-only assignments, and
 * safe authorization projections.
 *
 * <p>All dynamic values are bound parameters. SQL fragments used for ordering
 * are selected only from the allowlisted enum map below.</p>
 */
@Component
class JdbcStaffBranchAssignmentPersistenceAdapter
        implements StaffScopeQuery, StaffScopeStore, StaffBranchAssignmentQuery,
        StaffBranchAssignmentStore, StaffBranchAssignmentAdminQuery, AuthorizedBranchQuery,
        StaffAssignmentAuthorizationQuery {

    private static final String ASSIGNMENT_COLUMNS = """
            a.id, a.user_id, a.branch_id, a.status, a.assigned_at,
            a.assigned_by_user_id, a.ended_at, a.ended_by_user_id,
            a.end_reason, a.version
            """;

    private static final Map<StaffBranchAssignmentSortField, String> SORT_COLUMNS = Map.of(
            StaffBranchAssignmentSortField.ASSIGNED_AT, "a.assigned_at",
            StaffBranchAssignmentSortField.ENDED_AT, "a.ended_at",
            StaffBranchAssignmentSortField.STATUS, "a.status",
            StaffBranchAssignmentSortField.STAFF_IDENTIFIER, "lower(u.username)",
            StaffBranchAssignmentSortField.BRANCH_CODE, "b.code");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffBranchAssignmentPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAuthorizationContext> findAuthorizationContext(UUID userId) {
        requireId(userId, "userId");
        try {
            List<ScopeRows> rows = jdbcTemplate.query("""
                    select s.user_id, s.scope_type, u.status,
                           r.role_code, a.branch_id
                      from gym.staff_scopes s
                      join gym.users u on u.id = s.user_id
                      join gym.user_roles ur on ur.user_id = u.id
                      join gym.roles r on r.id = ur.role_id
                      left join gym.staff_branch_assignments a
                        on a.user_id = s.user_id
                       and a.status = 'ACTIVE'
                       and u.status = 'ACTIVE'
                     where s.user_id = :userId
                     order by r.role_code, a.branch_id
                    """,
                    new MapSqlParameterSource("userId", userId),
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapScopeRow);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            ScopeRows first = rows.getFirst();
            Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
            Set<UUID> branches = new LinkedHashSet<>();
            for (ScopeRows row : rows) {
                roles.add(row.role());
                if (row.branchId() != null) {
                    branches.add(row.branchId());
                }
            }
            return Optional.of(new StaffAuthorizationContext(
                    first.userId(),
                    roles,
                    first.status(),
                    first.scopeType(),
                    branches));
        } catch (DataAccessException | IllegalArgumentException exception) {
            if (exception instanceof DataAccessException dataAccessException) {
                throw new StaffScopeDataAccessException(
                        "Staff authorization facts could not be read.", dataAccessException);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffScopeDetails> findScope(UUID userId) {
        requireId(userId, "userId");
        try {
            List<ScopeDetailsRow> rows = jdbcTemplate.query("""
                    select s.user_id, s.scope_type, s.granted_at,
                           s.granted_by_user_id, s.version,
                           r.role_code
                      from gym.staff_scopes s
                      join gym.user_roles ur on ur.user_id = s.user_id
                      join gym.roles r on r.id = ur.role_id
                     where s.user_id = :userId
                     order by r.role_code
                    """,
                    new MapSqlParameterSource("userId", userId),
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapScopeDetailsRow);
            return rows.isEmpty()
                    ? Optional.empty()
                    : Optional.of(toScopeDetails(rows));
        } catch (DataAccessException exception) {
            throw new StaffScopeDataAccessException(
                    "Staff scope could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public StaffScopeDetails update(
            ChangeStaffScopeCommand command,
            UUID actorUserId,
            Instant occurredAt) {
        Objects.requireNonNull(command, "Scope change command is required.");
        requireId(actorUserId, "actorUserId");
        requireInstant(occurredAt);
        try {
            int updated = jdbcTemplate.update("""
                    update gym.staff_scopes
                       set scope_type = :scopeType,
                           granted_at = :occurredAt,
                           granted_by_user_id = :actorUserId,
                           version = version + 1
                     where user_id = :targetUserId
                       and version = :expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("scopeType", command.requestedScope().name())
                    .addValue("occurredAt", offset(occurredAt))
                    .addValue("actorUserId", actorUserId)
                    .addValue("targetUserId", command.targetUserId())
                    .addValue("expectedVersion", command.expectedVersion()));
            if (updated == 0) {
                classifyScopeUpdate(command.targetUserId(), command.expectedVersion());
            }
            return findScope(command.targetUserId())
                    .orElseThrow(StaffScopeNotFoundException::new);
        } catch (StaffScopeNotFoundException | StaffScopeVersionConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new StaffScopeDataAccessException(
                    "Staff scope could not be changed.", exception);
        } catch (DataAccessException exception) {
            throw new StaffScopeDataAccessException(
                    "Staff scope could not be changed.", exception);
        }
    }

    @Override
    @Transactional
    public StaffBranchAssignmentDetails assign(
            AssignStaffToBranchCommand command,
            UUID actorUserId,
            Instant occurredAt) {
        Objects.requireNonNull(command, "Assignment command is required.");
        requireId(actorUserId, "actorUserId");
        requireInstant(occurredAt);
        UUID assignmentId = UUID.randomUUID();
        try {
            List<StaffBranchAssignmentDetails> rows = jdbcTemplate.query("""
                    insert into gym.staff_branch_assignments (
                        id, user_id, branch_id, status, assigned_at,
                        assigned_by_user_id, version)
                    select :id, u.id, b.id, 'ACTIVE', :occurredAt,
                           :actorUserId, 0
                      from gym.users u
                      join gym.staff_scopes s on s.user_id = u.id
                      join gym.gym_branches b
                        on b.id = :branchId
                       and b.status = 'ACTIVE'
                       and b.organization_id = (
                           select id from gym.organizations where is_canonical = true)
                     where u.id = :targetUserId
                       and u.status = 'ACTIVE'
                    returning id, user_id, branch_id, status, assigned_at,
                              assigned_by_user_id, ended_at, ended_by_user_id,
                              end_reason, version
                    """, new MapSqlParameterSource()
                    .addValue("id", assignmentId)
                    .addValue("targetUserId", command.targetUserId())
                    .addValue("branchId", command.branchId())
                    .addValue("actorUserId", actorUserId)
                    .addValue("occurredAt", offset(occurredAt)),
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapAssignment);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            classifyAssignmentTarget(command.targetUserId(), command.branchId());
            throw new StaffBranchAssignmentDataAccessException(
                    "The staff branch assignment could not be created.",
                    new IllegalStateException("The assignment was not inserted."));
        } catch (StaffBranchAssignmentDuplicateException
                 | StaffBranchAssignmentNotFoundException
                 | StaffBranchAssignmentStateConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new StaffBranchAssignmentDuplicateException();
            }
            throw new StaffBranchAssignmentDataAccessException(
                    "The staff branch assignment could not be created.", exception);
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "The staff branch assignment could not be created.", exception);
        }
    }

    @Override
    @Transactional
    public StaffBranchAssignmentDetails end(
            EndStaffBranchAssignmentCommand command,
            UUID actorUserId,
            Instant occurredAt) {
        Objects.requireNonNull(command, "Assignment end command is required.");
        requireId(actorUserId, "actorUserId");
        requireInstant(occurredAt);
        try {
            List<StaffBranchAssignmentDetails> rows = jdbcTemplate.query("""
                    update gym.staff_branch_assignments
                       set status = 'ENDED',
                           ended_at = :occurredAt,
                           ended_by_user_id = :actorUserId,
                           end_reason = :reason,
                           version = version + 1
                     where id = :assignmentId
                       and status = 'ACTIVE'
                       and version = :expectedVersion
                    returning id, user_id, branch_id, status, assigned_at,
                              assigned_by_user_id, ended_at, ended_by_user_id,
                              end_reason, version
                    """, new MapSqlParameterSource()
                    .addValue("assignmentId", command.assignmentId())
                    .addValue("actorUserId", actorUserId)
                    .addValue("occurredAt", offset(occurredAt))
                    .addValue("reason", command.reason())
                    .addValue("expectedVersion", command.expectedVersion()),
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapAssignment);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
            classifyAssignmentEnd(command.assignmentId(), command.expectedVersion());
            throw new StaffBranchAssignmentDataAccessException(
                    "The staff branch assignment could not be ended.",
                    new IllegalStateException("The assignment was not updated."));
        } catch (StaffBranchAssignmentNotFoundException
                 | StaffBranchAssignmentVersionConflictException
                 | StaffBranchAssignmentStateConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "The staff branch assignment could not be ended.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffBranchAssignmentDetails> findById(UUID assignmentId) {
        requireId(assignmentId, "assignmentId");
        try {
            List<StaffBranchAssignmentDetails> rows = jdbcTemplate.query(
                    "select " + ASSIGNMENT_COLUMNS + " "
                            + "from gym.staff_branch_assignments a "
                            + "where a.id = :assignmentId",
                    new MapSqlParameterSource("assignmentId", assignmentId),
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapAssignment);
            return rows.stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Staff branch assignment could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StaffBranchAssignmentPage findPage(UUID userId, int page, int size) {
        requireId(userId, "userId");
        if (page < 0 || size < 1 || size > StaffBranchAssignmentPage.MAX_SIZE) {
            throw new IllegalArgumentException("Assignment page bounds are invalid.");
        }
        StaffBranchAssignmentSearchPage result = findAdministrativePage(
                new StaffBranchAssignmentSearchQuery(
                        userId, null, null, null, null, null, null, null,
                        page, size,
                        StaffBranchAssignmentSortField.ASSIGNED_AT,
                        StaffBranchAssignmentSortDirection.DESC));
        return new StaffBranchAssignmentPage(
                result.items().stream().map(StaffBranchAssignmentSearchResult::assignment).toList(),
                page, size, result.totalElements(), result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffBranchAssignmentDetails> findActive(UUID userId) {
        requireId(userId, "userId");
        StaffBranchAssignmentSearchQuery query = new StaffBranchAssignmentSearchQuery(
                userId, null, null, null, null, StaffBranchAssignmentStatus.ACTIVE,
                null, null, 0, StaffBranchAssignmentSearchQuery.MAX_SIZE,
                StaffBranchAssignmentSortField.BRANCH_CODE,
                StaffBranchAssignmentSortDirection.ASC);
        return findAdministrativePage(query).items().stream()
                .map(StaffBranchAssignmentSearchResult::assignment)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffBranchAssignmentDetails> findActiveByBranch(UUID branchId) {
        requireId(branchId, "branchId");
        StaffBranchAssignmentSearchQuery query = new StaffBranchAssignmentSearchQuery(
                null, null, null, null, branchId, StaffBranchAssignmentStatus.ACTIVE,
                null, null, 0, StaffBranchAssignmentSearchQuery.MAX_SIZE,
                StaffBranchAssignmentSortField.STAFF_IDENTIFIER,
                StaffBranchAssignmentSortDirection.ASC);
        return findAdministrativePage(query).items().stream()
                .map(StaffBranchAssignmentSearchResult::assignment)
                .toList();
    }

    /** Full bounded administrative projection used by future application services. */
    @Transactional(readOnly = true)
    public StaffBranchAssignmentSearchPage findPage(
            StaffBranchAssignmentSearchQuery query) {
        Objects.requireNonNull(query, "Assignment search query is required.");
        MapSqlParameterSource parameters = searchParameters(query);
        String from = assignmentSearchFrom();
        String filters = assignmentSearchFilters();
        try {
            Long total = jdbcTemplate.queryForObject(
                    "select count(*) " + from + filters,
                    parameters,
                    Long.class);
            String select = "select " + ASSIGNMENT_COLUMNS + """
                    , u.username,
                       r.role_code, s.scope_type,
                       b.id as summary_branch_id,
                       b.organization_id as summary_organization_id,
                       b.code as summary_branch_code,
                       b.name as summary_branch_name,
                       b.timezone as summary_branch_timezone,
                       b.is_initial_branch as summary_initial_branch
                    """ + from + filters + orderBy(query)
                    + " limit :limit offset :offset";
            List<StaffBranchAssignmentSearchResult> items = jdbcTemplate.query(
                    select,
                    parameters,
                    JdbcStaffBranchAssignmentPersistenceAdapter::mapSearchResult);
            long safeTotal = total == null ? 0L : total;
            int totalPages = safeTotal == 0L
                    ? 0 : (int) ((safeTotal + query.size() - 1) / query.size());
            return new StaffBranchAssignmentSearchPage(
                    items, query.page(), query.size(), safeTotal, totalPages);
        } catch (DataAccessException | IllegalArgumentException exception) {
            if (exception instanceof DataAccessException dataAccessException) {
                throw new StaffBranchAssignmentDataAccessException(
                        "Staff branch assignments could not be searched.", dataAccessException);
            }
            throw exception;
        }
    }

    private StaffBranchAssignmentSearchPage findAdministrativePage(
            StaffBranchAssignmentSearchQuery query) {
        return findPage(query);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findAuthorizedOrganizationId(UUID userId) {
        requireId(userId, "userId");
        try {
            List<UUID> organizationIds = jdbcTemplate.query("""
                    select o.id
                      from gym.organizations o
                      join gym.users u on u.id = :userId and u.status = 'ACTIVE'
                     where o.is_canonical = true
                       and o.status = 'ACTIVE'
                    """, new MapSqlParameterSource("userId", userId),
                    (rs, row) -> rs.getObject("id", UUID.class));
            return organizationIds.stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException exception) {
            if (exception instanceof DataAccessException dataAccessException) {
                throw new StaffBranchAssignmentDataAccessException(
                        "Authorized organization could not be read.", dataAccessException);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorizedBranchSummary> findAuthorizedActiveBranches(UUID userId) {
        requireId(userId, "userId");
        try {
            return jdbcTemplate.query("""
                    select b.id, b.organization_id, b.code, b.name,
                           b.timezone, b.is_initial_branch
                      from gym.gym_branches b
                      join gym.organizations o on o.id = b.organization_id
                      join gym.users u on u.id = :userId and u.status = 'ACTIVE'
                     where o.is_canonical = true
                       and o.status = 'ACTIVE'
                       and b.status = 'ACTIVE'
                       and (
                           exists (
                               select 1
                                 from gym.staff_scopes s
                                 join gym.user_roles ur on ur.user_id = s.user_id
                                 join gym.roles r on r.id = ur.role_id
                                where s.user_id = u.id
                                  and s.scope_type = 'ORGANIZATION'
                                  and r.role_code = 'ADMIN')
                           or exists (
                               select 1
                                 from gym.staff_scopes s
                                 join gym.staff_branch_assignments a
                                   on a.user_id = s.user_id
                                  and a.branch_id = b.id
                                  and a.status = 'ACTIVE'
                                 join gym.user_roles ur on ur.user_id = s.user_id
                                 join gym.roles r on r.id = ur.role_id
                                where s.user_id = u.id
                                  and s.scope_type = 'BRANCH'
                                  and r.role_code in ('ADMIN', 'RECEPTIONIST'))
                       )
                     order by b.code, b.id
                    """, new MapSqlParameterSource("userId", userId),
                    (rs, row) -> new AuthorizedBranchSummary(
                            rs.getObject("id", UUID.class),
                            rs.getObject("organization_id", UUID.class),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("timezone"),
                            rs.getBoolean("is_initial_branch")));
        } catch (DataAccessException | IllegalArgumentException exception) {
            if (exception instanceof DataAccessException dataAccessException) {
                throw new StaffBranchAssignmentDataAccessException(
                        "Authorized branches could not be read.", dataAccessException);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public boolean lockAuthorizedActiveBranchForOperation(
            UUID userId, UUID branchId, StaffScopeType scopeType) {
        requireId(userId, "userId");
        requireId(branchId, "branchId");
        Objects.requireNonNull(scopeType, "scopeType is required");
        String sql = scopeType == StaffScopeType.ORGANIZATION
                ? """
                    select b.id
                      from gym.gym_branches b
                      join gym.organizations o on o.id = b.organization_id
                     where b.id = :branchId
                       and b.status = 'ACTIVE'
                       and o.is_canonical = true
                       and o.status = 'ACTIVE'
                       and exists (
                           select 1
                             from gym.users u
                             join gym.staff_scopes s on s.user_id = u.id
                             join gym.user_roles ur on ur.user_id = u.id
                             join gym.roles r on r.id = ur.role_id
                            where u.id = :userId
                              and u.status = 'ACTIVE'
                              and s.scope_type = 'ORGANIZATION'
                              and r.role_code = 'ADMIN')
                     for share of b
                    """
                : """
                    select b.id
                      from gym.gym_branches b
                      join gym.organizations o on o.id = b.organization_id
                      join gym.staff_branch_assignments a
                        on a.branch_id = b.id
                       and a.user_id = :userId
                       and a.status = 'ACTIVE'
                     where b.id = :branchId
                       and b.status = 'ACTIVE'
                       and o.is_canonical = true
                       and o.status = 'ACTIVE'
                       and exists (
                           select 1
                             from gym.users u
                             join gym.staff_scopes s on s.user_id = u.id
                             join gym.user_roles ur on ur.user_id = u.id
                             join gym.roles r on r.id = ur.role_id
                            where u.id = :userId
                              and u.status = 'ACTIVE'
                              and s.scope_type = 'BRANCH'
                              and r.role_code in ('ADMIN', 'RECEPTIONIST'))
                     for share of b, a
                    """;
        try {
            List<UUID> locked = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("branchId", branchId),
                    (rs, row) -> rs.getObject("id", UUID.class));
            return !locked.isEmpty();
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "The active branch operation could not be secured.", exception);
        }
    }

    @Override
    @Transactional
    public void lockOrganizationAdministratorLifecycle() {
        try {
            jdbcTemplate.query("""
                    select s.user_id
                      from gym.staff_scopes s
                      join gym.users u on u.id = s.user_id and u.status = 'ACTIVE'
                      join gym.user_roles ur on ur.user_id = s.user_id
                      join gym.roles r on r.id = ur.role_id and r.role_code = 'ADMIN'
                     where s.scope_type = 'ORGANIZATION'
                     order by s.user_id
                     for update of s
                    """, new MapSqlParameterSource(), (rs, row) -> rs.getObject("user_id", UUID.class));
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Organization administrator lifecycle could not be locked.", exception);
        }
    }

    @Override
    @Transactional
    public void lockStaffLifecycle(UUID userId) {
        requireId(userId, "userId");
        try {
            jdbcTemplate.query("""
                    select user_id
                      from gym.staff_scopes
                     where user_id = :userId
                     for update
                    """, new MapSqlParameterSource("userId", userId),
                    (rs, row) -> rs.getObject("user_id", UUID.class));
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Staff assignment lifecycle could not be locked.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveOrganizationAdministrators() {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    select count(*)
                      from gym.staff_scopes s
                      join gym.users u on u.id = s.user_id and u.status = 'ACTIVE'
                      join gym.user_roles ur on ur.user_id = s.user_id
                      join gym.roles r on r.id = ur.role_id and r.role_code = 'ADMIN'
                     where s.scope_type = 'ORGANIZATION'
                    """, new MapSqlParameterSource(), Long.class);
            return count == null ? 0L : count;
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Organization administrator facts could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLastActiveOrganizationAdministrator(UUID userId) {
        requireId(userId, "userId");
        try {
            Boolean target = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1
                          from gym.staff_scopes s
                          join gym.users u on u.id = s.user_id and u.status = 'ACTIVE'
                          join gym.user_roles ur on ur.user_id = s.user_id
                          join gym.roles r on r.id = ur.role_id and r.role_code = 'ADMIN'
                         where s.user_id = :userId
                           and s.scope_type = 'ORGANIZATION')
                    """, new MapSqlParameterSource("userId", userId), Boolean.class);
            return Boolean.TRUE.equals(target) && countActiveOrganizationAdministrators() == 1L;
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Organization administrator facts could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBranchAssignment(UUID userId, UUID branchId) {
        requireId(userId, "userId");
        requireId(branchId, "branchId");
        return exists("""
                select exists (
                    select 1 from gym.staff_branch_assignments
                     where user_id = :userId and branch_id = :branchId
                       and status = 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("userId", userId).addValue("branchId", branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnotherActiveBranchAssignment(UUID userId, UUID excludingAssignmentId) {
        requireId(userId, "userId");
        requireId(excludingAssignmentId, "excludingAssignmentId");
        return exists("""
                select exists (
                    select 1 from gym.staff_branch_assignments
                     where user_id = :userId and id <> :excludingAssignmentId
                       and status = 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("excludingAssignmentId", excludingAssignmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean branchAdministratorControlsBranch(UUID actorUserId, UUID branchId) {
        requireId(actorUserId, "actorUserId");
        requireId(branchId, "branchId");
        return exists("""
                select exists (
                    select 1
                      from gym.staff_scopes s
                      join gym.users u on u.id = s.user_id and u.status = 'ACTIVE'
                      join gym.user_roles ur on ur.user_id = s.user_id
                      join gym.roles r on r.id = ur.role_id and r.role_code = 'ADMIN'
                      join gym.staff_branch_assignments a
                        on a.user_id = s.user_id and a.status = 'ACTIVE'
                       and a.branch_id = :branchId
                      join gym.gym_branches b
                        on b.id = a.branch_id and b.status = 'ACTIVE'
                     where s.user_id = :actorUserId
                       and s.scope_type = 'BRANCH')
                """, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("branchId", branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean branchAdministratorControlsTarget(UUID actorUserId, UUID targetUserId) {
        requireId(actorUserId, "actorUserId");
        requireId(targetUserId, "targetUserId");
        return exists("""
                select exists (
                    select 1
                      from gym.staff_scopes actor_scope
                      join gym.users actor on actor.id = actor_scope.user_id
                       and actor.status = 'ACTIVE'
                      join gym.user_roles actor_role on actor_role.user_id = actor.id
                      join gym.roles actor_role_code on actor_role_code.id = actor_role.role_id
                       and actor_role_code.role_code = 'ADMIN'
                      join gym.staff_branch_assignments actor_assignment
                        on actor_assignment.user_id = actor.id
                       and actor_assignment.status = 'ACTIVE'
                      join gym.gym_branches actor_branch
                        on actor_branch.id = actor_assignment.branch_id
                       and actor_branch.status = 'ACTIVE'
                      join gym.users target on target.id = :targetUserId
                       and target.status = 'ACTIVE'
                      join gym.staff_branch_assignments target_assignment
                        on target_assignment.branch_id = actor_assignment.branch_id
                       and target_assignment.user_id = :targetUserId
                       and target_assignment.status = 'ACTIVE'
                     where actor_scope.user_id = :actorUserId
                       and actor_scope.scope_type = 'BRANCH')
                """, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("targetUserId", targetUserId));
    }

    private void classifyScopeUpdate(UUID userId, long expectedVersion) {
        Optional<Long> current = jdbcTemplate.query("""
                select version from gym.staff_scopes where user_id = :userId
                """, new MapSqlParameterSource("userId", userId),
                (rs, row) -> rs.getLong("version"))
                .stream().findFirst();
        if (current.isEmpty()) {
            throw new StaffScopeNotFoundException();
        }
        throw new StaffScopeVersionConflictException(userId);
    }

    private void classifyAssignmentTarget(UUID userId, UUID branchId) {
        boolean userExists = exists(
                "select exists(select 1 from gym.users where id = :userId and status = 'ACTIVE')",
                new MapSqlParameterSource("userId", userId));
        if (!userExists) {
            throw new StaffBranchAssignmentNotFoundException();
        }
        boolean branchExists = exists("""
                select exists(
                    select 1 from gym.gym_branches b
                     where b.id = :branchId and b.status = 'ACTIVE'
                       and b.organization_id = (
                           select id from gym.organizations where is_canonical = true))
                """, new MapSqlParameterSource("branchId", branchId));
        if (!branchExists) {
            throw new StaffBranchAssignmentStateConflictException(
                    "The assignment branch is not active.");
        }
        throw new StaffBranchAssignmentDataAccessException(
                "The staff branch assignment could not be created.",
                new IllegalStateException("The assignment target has no staff scope."));
    }

    private void classifyAssignmentEnd(UUID assignmentId, long expectedVersion) {
        List<AssignmentState> rows = jdbcTemplate.query("""
                select status, version
                  from gym.staff_branch_assignments
                 where id = :assignmentId
                """, new MapSqlParameterSource("assignmentId", assignmentId),
                (rs, row) -> new AssignmentState(
                        StaffBranchAssignmentStatus.valueOf(rs.getString("status")),
                        rs.getLong("version")));
        if (rows.isEmpty()) {
            throw new StaffBranchAssignmentNotFoundException();
        }
        AssignmentState state = rows.getFirst();
        if (state.status() != StaffBranchAssignmentStatus.ACTIVE) {
            throw new StaffBranchAssignmentStateConflictException(
                    "Only an active assignment can be ended.");
        }
        throw new StaffBranchAssignmentVersionConflictException(assignmentId);
    }

    private boolean exists(String sql, MapSqlParameterSource parameters) {
        try {
            Boolean value = jdbcTemplate.queryForObject(sql, parameters, Boolean.class);
            return Boolean.TRUE.equals(value);
        } catch (DataAccessException exception) {
            throw new StaffBranchAssignmentDataAccessException(
                    "Staff assignment facts could not be read.", exception);
        }
    }

    private static String assignmentSearchFrom() {
        return """
                from gym.staff_branch_assignments a
                join gym.users u on u.id = a.user_id
                join gym.user_roles ur on ur.user_id = a.user_id
                join gym.roles r on r.id = ur.role_id
                join gym.staff_scopes s on s.user_id = a.user_id
                join gym.gym_branches b on b.id = a.branch_id
                """;
    }

    private static String assignmentSearchFilters() {
        return """
                where (cast(:userId as uuid) is null or a.user_id = cast(:userId as uuid))
                  and (cast(:branchId as uuid) is null or a.branch_id = cast(:branchId as uuid))
                  and (cast(:role as varchar) is null or r.role_code = cast(:role as varchar))
                  and (cast(:scopeType as varchar) is null
                       or s.scope_type = cast(:scopeType as varchar))
                  and (cast(:status as varchar) is null or a.status = cast(:status as varchar))
                  and (cast(:assignedFrom as timestamptz) is null
                       or a.assigned_at >= cast(:assignedFrom as timestamptz))
                  and (cast(:assignedTo as timestamptz) is null
                       or a.assigned_at <= cast(:assignedTo as timestamptz))
                  and (cast(:search as varchar) is null
                       or lower(u.username) like cast(:searchPattern as varchar) escape '\\'
                       or lower(b.code) like cast(:searchPattern as varchar) escape '\\'
                       or lower(b.name) like cast(:searchPattern as varchar) escape '\\')
                """;
    }

    private static String orderBy(StaffBranchAssignmentSearchQuery query) {
        String column = SORT_COLUMNS.get(query.sortField());
        if (column == null) {
            throw new IllegalArgumentException("Unsupported assignment sort field.");
        }
        String direction = query.direction() == StaffBranchAssignmentSortDirection.ASC
                ? "asc" : "desc";
        return " order by " + column + " " + direction + ", a.id asc";
    }

    private static MapSqlParameterSource searchParameters(
            StaffBranchAssignmentSearchQuery query) {
        String pattern = query.search() == null ? null
                : "%" + escapeLike(query.search().toLowerCase(Locale.ROOT)) + "%";
        return new MapSqlParameterSource()
                .addValue("userId", query.userId())
                .addValue("branchId", query.branchId())
                .addValue("role", query.role() == null ? null : query.role().name())
                .addValue("scopeType", query.scopeType() == null ? null : query.scopeType().name())
                .addValue("status", query.status() == null ? null : query.status().name())
                .addValue("assignedFrom", offset(query.assignedFrom()))
                .addValue("assignedTo", offset(query.assignedTo()))
                .addValue("search", query.search())
                .addValue("searchPattern", pattern)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
    }

    private static StaffScopeDetails toScopeDetails(List<ScopeDetailsRow> rows) {
        ScopeDetailsRow first = rows.getFirst();
        Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        for (ScopeDetailsRow row : rows) {
            roles.add(row.role());
        }
        return new StaffScopeDetails(
                first.userId(), roles, first.scopeType(), first.grantedAt(),
                first.grantedByUserId(), first.version());
    }

    private static StaffBranchAssignmentSearchResult mapSearchResult(
            ResultSet rs, int row) throws SQLException {
        StaffBranchAssignmentDetails assignment = mapAssignment(rs, row);
        AuthorizedBranchSummary branch = new AuthorizedBranchSummary(
                rs.getObject("summary_branch_id", UUID.class),
                rs.getObject("summary_organization_id", UUID.class),
                rs.getString("summary_branch_code"),
                rs.getString("summary_branch_name"),
                rs.getString("summary_branch_timezone"),
                rs.getBoolean("summary_initial_branch"));
        return new StaffBranchAssignmentSearchResult(
                assignment,
                rs.getString("username"),
                RoleCode.valueOf(rs.getString("role_code")),
                StaffScopeType.valueOf(rs.getString("scope_type")),
                branch);
    }

    private static StaffBranchAssignmentDetails mapAssignment(ResultSet rs, int row)
            throws SQLException {
        return new StaffBranchAssignmentDetails(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                StaffBranchAssignmentStatus.valueOf(rs.getString("status")),
                instant(rs, "assigned_at"),
                rs.getObject("assigned_by_user_id", UUID.class),
                instant(rs, "ended_at"),
                rs.getObject("ended_by_user_id", UUID.class),
                rs.getString("end_reason"),
                rs.getLong("version"));
    }

    private static ScopeRows mapScopeRow(ResultSet rs, int row) throws SQLException {
        return new ScopeRows(
                rs.getObject("user_id", UUID.class),
                StaffScopeType.valueOf(rs.getString("scope_type")),
                StaffAccountStatus.valueOf(rs.getString("status")),
                RoleCode.valueOf(rs.getString("role_code")),
                rs.getObject("branch_id", UUID.class));
    }

    private static ScopeDetailsRow mapScopeDetailsRow(ResultSet rs, int row)
            throws SQLException {
        return new ScopeDetailsRow(
                rs.getObject("user_id", UUID.class),
                StaffScopeType.valueOf(rs.getString("scope_type")),
                instant(rs, "granted_at"),
                rs.getObject("granted_by_user_id", UUID.class),
                rs.getLong("version"),
                RoleCode.valueOf(rs.getString("role_code")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static void requireId(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }

    private static void requireInstant(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("Persistence timestamp is required.");
        }
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

    private record ScopeRows(
            UUID userId,
            StaffScopeType scopeType,
            StaffAccountStatus status,
            RoleCode role,
            UUID branchId) {
    }

    private record ScopeDetailsRow(
            UUID userId,
            StaffScopeType scopeType,
            Instant grantedAt,
            UUID grantedByUserId,
            long version,
            RoleCode role) {
    }

    private record AssignmentState(StaffBranchAssignmentStatus status, long version) {
    }
}
