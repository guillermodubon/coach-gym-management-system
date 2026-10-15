package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityValuePolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.application.StaffAccountProvisioningDraft;
import io.github.guillermodubon.coachgym.user.application.StaffAccountProvisioningStore;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC boundary that creates one accepted identity and all required authority rows. */
@Repository
class JdbcStaffAccountProvisioningAdapter implements StaffAccountProvisioningStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffAccountProvisioningAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailInUse(String normalizedEmail) {
        String email = StaffIdentityValuePolicy.normalizeEmail(normalizedEmail);
        try {
            Boolean exists = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from gym.users where lower(email) = :email)
                    """, new MapSqlParameterSource("email", email), Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException exception) {
            throw new StaffIdentityDataAccessException(
                    "Staff identity data could not be accessed.");
        }
    }

    @Override
    @Transactional
    public void provision(StaffAccountProvisioningDraft draft) {
        requireDraft(draft);
        String email = StaffIdentityValuePolicy.normalizeEmail(draft.email());
        Set<UUID> branches = StaffInvitationPolicy.requireValidProposal(
                draft.role(), draft.scope(), draft.branchIds());
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(Set.of(draft.role()), draft.scope());
        var occurredAt = draft.provisionedAt().atOffset(ZoneOffset.UTC);
        try {
            int users = jdbcTemplate.update("""
                    insert into gym.users (
                        id, username, email, password_hash, first_name, last_name,
                        status, created_at, updated_at, version)
                    values (
                        :userId, :username, :email, :passwordHash, :firstName, :lastName,
                        'ACTIVE', :occurredAt, :occurredAt, 0)
                    """, new MapSqlParameterSource()
                    .addValue("userId", draft.userId())
                    .addValue("username", generatedUsername(draft.userId()))
                    .addValue("email", email)
                    .addValue("passwordHash", draft.passwordHash())
                    .addValue("firstName", draft.firstName())
                    .addValue("lastName", draft.lastName())
                    .addValue("occurredAt", occurredAt));
            requireOne(users, "The staff account could not be created.");

            int roles = jdbcTemplate.update("""
                    insert into gym.user_roles (user_id, role_id, granted_at, granted_by_user_id)
                    select :userId, role.id, :occurredAt, :inviterId
                      from gym.roles role
                     where role.role_code = :roleCode
                       and role.is_active
                    """, new MapSqlParameterSource()
                    .addValue("userId", draft.userId())
                    .addValue("occurredAt", occurredAt)
                    .addValue("inviterId", draft.invitedByUserId())
                    .addValue("roleCode", draft.role().name()));
            requireOne(roles, "The approved staff role is unavailable.");

            int scopes = jdbcTemplate.update("""
                    insert into gym.staff_scopes (
                        user_id, scope_type, granted_at, granted_by_user_id, version)
                    values (:userId, :scopeType, :occurredAt, :inviterId, 0)
                    """, new MapSqlParameterSource()
                    .addValue("userId", draft.userId())
                    .addValue("scopeType", draft.scope().name())
                    .addValue("occurredAt", occurredAt)
                    .addValue("inviterId", draft.invitedByUserId()));
            requireOne(scopes, "The staff scope could not be created.");

            for (UUID branchId : branches) {
                int assignments = jdbcTemplate.update("""
                        insert into gym.staff_branch_assignments (
                            id, user_id, branch_id, status, assigned_at,
                            assigned_by_user_id, version)
                        select :assignmentId, :userId, branch.id, 'ACTIVE',
                               :occurredAt, :inviterId, 0
                          from gym.gym_branches branch
                          join gym.organizations organization
                            on organization.id = branch.organization_id
                         where branch.id = :branchId
                           and branch.status = 'ACTIVE'
                           and organization.is_canonical = true
                           and organization.status = 'ACTIVE'
                        """, new MapSqlParameterSource()
                        .addValue("assignmentId", UUID.randomUUID())
                        .addValue("userId", draft.userId())
                        .addValue("occurredAt", occurredAt)
                        .addValue("inviterId", draft.invitedByUserId())
                        .addValue("branchId", branchId));
                requireOne(assignments, "A proposed staff branch is no longer active.");
            }
        } catch (StaffIdentityStateConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new StaffIdentityStateConflictException(
                        "Invitation is not available.");
            }
            throw new StaffIdentityDataAccessException(
                    "Staff account provisioning could not be completed.");
        } catch (DataAccessException exception) {
            throw new StaffIdentityDataAccessException(
                    "Staff account provisioning could not be completed.");
        }
    }

    private static void requireDraft(StaffAccountProvisioningDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Staff account provisioning data is required.");
        }
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) {
            throw new StaffIdentityStateConflictException(message);
        }
    }

    private static String generatedUsername(UUID userId) {
        return "staff-" + userId.toString().replace("-", "");
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
