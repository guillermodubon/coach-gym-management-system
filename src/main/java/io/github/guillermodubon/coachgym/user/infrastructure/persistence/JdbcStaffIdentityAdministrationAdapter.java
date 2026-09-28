package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.ChangeStaffRoleScopeCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAdministrationState;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAdministrationStore;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityVersionConflictException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC mutations for security-sensitive staff account status and authority. */
@Repository
class JdbcStaffIdentityAdministrationAdapter implements StaffIdentityAdministrationStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffIdentityAdministrationAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffIdentityAdministrationState> find(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Staff identity is required.");
        }
        try {
            List<StateRow> rows = jdbcTemplate.query("""
                    select u.id, u.status, u.security_version,
                           s.scope_type, s.version as scope_version, r.role_code
                      from gym.users u
                      join gym.staff_scopes s on s.user_id = u.id
                      join gym.user_roles ur on ur.user_id = u.id
                      join gym.roles r on r.id = ur.role_id
                     where u.id = :userId
                     order by r.role_code
                    """, new MapSqlParameterSource("userId", userId),
                    (rs, row) -> new StateRow(
                            rs.getObject("id", UUID.class),
                            StaffAccountStatus.valueOf(rs.getString("status")),
                            rs.getLong("security_version"),
                            StaffScopeType.valueOf(rs.getString("scope_type")),
                            rs.getLong("scope_version"),
                            RoleCode.valueOf(rs.getString("role_code"))));
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            StateRow first = rows.getFirst();
            Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
            rows.forEach(row -> roles.add(row.role()));
            return Optional.of(new StaffIdentityAdministrationState(
                    first.userId(), first.status(), roles, first.scopeType(),
                    first.securityVersion(), first.scopeVersion()));
        } catch (DataAccessException | IllegalArgumentException exception) {
            if (exception instanceof DataAccessException) {
                throw StaffIdentityJdbcSupport.safeDataAccess((DataAccessException) exception);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public StaffIdentityAdministrationState changeStatus(
            UUID userId,
            StaffIdentityStatus expectedStatus,
            StaffIdentityStatus requestedStatus,
            long expectedSecurityVersion,
            UUID actorUserId,
            Instant occurredAt) {
        requireMutation(userId, actorUserId, occurredAt);
        try {
            int updated = jdbcTemplate.update("""
                    update gym.users
                       set status = :requestedStatus,
                           security_version = security_version + 1,
                           version = version + 1
                     where id = :userId
                       and status = :expectedStatus
                       and security_version = :expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("requestedStatus", requestedStatus.name())
                    .addValue("expectedStatus", expectedStatus.name())
                    .addValue("expectedVersion", expectedSecurityVersion));
            if (updated != 1) {
                classifyVersion(userId);
            }
            if (requestedStatus != StaffIdentityStatus.ACTIVE) {
                jdbcTemplate.update("""
                        update gym.staff_password_recovery_tokens
                           set status = 'REVOKED',
                               revoked_at = :occurredAt,
                               version = version + 1
                         where user_id = :userId
                           and status = 'PENDING'
                        """, new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("occurredAt", databaseTime(occurredAt)));
            }
            return find(userId).orElseThrow(StaffIdentityNotFoundException::new);
        } catch (StaffIdentityNotFoundException | StaffIdentityVersionConflictException
                 | StaffIdentityStateConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new StaffIdentityStateConflictException(
                    "Staff account lifecycle transition could not be applied.");
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public StaffIdentityAdministrationState changeRoleScope(
            ChangeStaffRoleScopeCommand command,
            long expectedScopeVersion,
            UUID actorUserId,
            Instant occurredAt) {
        if (command == null) {
            throw new IllegalArgumentException("Staff role/scope command is required.");
        }
        requireMutation(command.targetUserId(), actorUserId, occurredAt);
        try {
            StaffIdentityAdministrationState current = find(command.targetUserId())
                    .orElseThrow(StaffIdentityNotFoundException::new);
            if (current.securityVersion() != command.expectedVersion()
                    || current.scopeVersion() != expectedScopeVersion) {
                throw new StaffIdentityVersionConflictException(command.targetUserId());
            }
            if (current.accountStatus() != StaffAccountStatus.ACTIVE) {
                throw new StaffIdentityStateConflictException(
                        "Only active staff authority can be changed.");
            }

            boolean updateRolesFirst = StaffScopeAuthorizationPolicy.isCompatible(
                    command.requestedRoles(), current.scopeType());
            if (updateRolesFirst) {
                updateRolesIfChanged(current, command, actorUserId, occurredAt);
                updateScopeIfChanged(current, command, actorUserId, occurredAt, expectedScopeVersion);
            } else {
                updateScopeIfChanged(current, command, actorUserId, occurredAt, expectedScopeVersion);
                updateRolesIfChanged(current, command, actorUserId, occurredAt);
            }

            int securityUpdate = jdbcTemplate.update("""
                    update gym.users
                       set security_version = security_version + 1,
                           version = version + 1
                     where id = :userId
                       and status = 'ACTIVE'
                       and security_version = :expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("userId", command.targetUserId())
                    .addValue("expectedVersion", command.expectedVersion()));
            if (securityUpdate != 1) {
                classifyVersion(command.targetUserId());
            }
            return find(command.targetUserId()).orElseThrow(StaffIdentityNotFoundException::new);
        } catch (StaffIdentityNotFoundException | StaffIdentityVersionConflictException
                 | StaffIdentityStateConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new StaffIdentityStateConflictException(
                    "Staff role/scope change could not be applied.");
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    private void updateRolesIfChanged(
            StaffIdentityAdministrationState current,
            ChangeStaffRoleScopeCommand command,
            UUID actorUserId,
            Instant occurredAt) {
        if (current.roles().equals(command.requestedRoles())) {
            return;
        }
        if (command.requestedRoles().size() != 1) {
            throw new StaffIdentityStateConflictException(
                    "Exactly one supported staff role must be persisted.");
        }
        RoleCode requestedRole = command.requestedRoles().iterator().next();
        int updated = jdbcTemplate.update("""
                update gym.user_roles assignment
                   set role_id = role.id,
                       granted_at = :occurredAt,
                       granted_by_user_id = :actorUserId
                  from gym.roles role
                 where assignment.user_id = :userId
                   and role.role_code = :roleCode
                   and role.is_active
                """, new MapSqlParameterSource()
                .addValue("userId", command.targetUserId())
                .addValue("roleCode", requestedRole.name())
                .addValue("actorUserId", actorUserId)
                .addValue("occurredAt", databaseTime(occurredAt)));
        if (updated != 1) {
            throw new StaffIdentityStateConflictException(
                    "The requested staff role is not available.");
        }
    }

    private void updateScopeIfChanged(
            StaffIdentityAdministrationState current,
            ChangeStaffRoleScopeCommand command,
            UUID actorUserId,
            Instant occurredAt,
            long expectedScopeVersion) {
        if (current.scopeType() == command.requestedScope()) {
            return;
        }
        int updated = jdbcTemplate.update("""
                update gym.staff_scopes
                   set scope_type = :scopeType,
                       granted_at = :occurredAt,
                       granted_by_user_id = :actorUserId,
                       version = version + 1
                 where user_id = :userId
                   and version = :expectedScopeVersion
                """, new MapSqlParameterSource()
                .addValue("scopeType", command.requestedScope().name())
                .addValue("occurredAt", databaseTime(occurredAt))
                .addValue("actorUserId", actorUserId)
                .addValue("userId", command.targetUserId())
                .addValue("expectedScopeVersion", expectedScopeVersion));
        if (updated != 1) {
            throw new StaffIdentityVersionConflictException(command.targetUserId());
        }
    }

    private void classifyVersion(UUID userId) {
        boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select exists(select 1 from gym.users where id = :userId)
                """, new MapSqlParameterSource("userId", userId), Boolean.class));
        if (!exists) {
            throw new StaffIdentityNotFoundException();
        }
        throw new StaffIdentityVersionConflictException(userId);
    }

    private static void requireMutation(UUID userId, UUID actorUserId, Instant occurredAt) {
        if (userId == null || actorUserId == null || occurredAt == null) {
            throw new IllegalArgumentException("Staff identity mutation data is required.");
        }
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record StateRow(
            UUID userId,
            StaffAccountStatus status,
            long securityVersion,
            StaffScopeType scopeType,
            long scopeVersion,
            RoleCode role) {
    }
}
