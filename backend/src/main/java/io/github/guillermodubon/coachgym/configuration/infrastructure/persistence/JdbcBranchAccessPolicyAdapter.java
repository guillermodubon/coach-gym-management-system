package io.github.guillermodubon.coachgym.configuration.infrastructure.persistence;

import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyNotFoundException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyStore;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyVersionConflictException;
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

/** PostgreSQL adapter for effective branch-policy reads and optimistic updates. */
@Repository
class JdbcBranchAccessPolicyAdapter implements BranchAccessPolicyQuery, BranchAccessPolicyStore {

    static final String FIND_FOR_BRANCH_SQL = """
            select organization.id as organization_id,
                   branch.id as branch_id,
                   settings.require_confirmed_payment_for_access
                       as organization_default_requires_payment,
                   coalesce(override.policy_mode, 'INHERIT') as branch_mode,
                   coalesce(override.version, 0) as version
            from gym.organizations as organization
            join gym.gym_branches as branch
                on branch.organization_id = organization.id
            join gym.gym_settings as settings
                on settings.id = 1
            left join gym.branch_access_policy_overrides as override
                on override.branch_id = branch.id
            where organization.id = :organizationId
              and organization.is_canonical
              and organization.status = 'ACTIVE'
              and branch.id = :branchId
              and branch.status = 'ACTIVE'
            """;

    static final String LOCK_ACTIVE_BRANCH_SQL = """
            select branch.id
            from gym.organizations as organization
            join gym.gym_branches as branch
                on branch.organization_id = organization.id
            where organization.id = :organizationId
              and organization.is_canonical
              and organization.status = 'ACTIVE'
              and branch.id = :branchId
              and branch.status = 'ACTIVE'
            for update of branch
            """;

    static final String UPDATE_SQL = """
            update gym.branch_access_policy_overrides as override
            set policy_mode = :mode,
                updated_by_user_id = :actorUserId,
                updated_at = :occurredAt,
                version = override.version + 1
            where override.branch_id = :branchId
              and override.version = :expectedVersion
              and exists (
                  select 1
                  from gym.gym_branches as branch
                  join gym.organizations as organization
                      on organization.id = branch.organization_id
                  where branch.id = override.branch_id
                    and branch.status = 'ACTIVE'
                    and organization.id = :organizationId
                    and organization.is_canonical
                    and organization.status = 'ACTIVE'
              )
            returning override.branch_id
            """;

    static final String INSERT_SQL = """
            insert into gym.branch_access_policy_overrides
                (branch_id, policy_mode, updated_by_user_id, updated_at, version)
            select branch.id, :mode, :actorUserId, :occurredAt, 1
            from gym.organizations as organization
            join gym.gym_branches as branch
                on branch.organization_id = organization.id
            where organization.id = :organizationId
              and organization.is_canonical
              and organization.status = 'ACTIVE'
              and branch.id = :branchId
              and branch.status = 'ACTIVE'
              and :expectedVersion = 0
            on conflict (branch_id) do nothing
            returning branch_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcBranchAccessPolicyAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public EffectiveBranchAccessPolicy findForBranch(
            UUID organizationId,
            UUID branchId) {
        requireIdentifiers(organizationId, branchId);
        try {
            List<EffectiveBranchAccessPolicy> results = jdbcTemplate.query(
                    FIND_FOR_BRANCH_SQL,
                    parameters(organizationId, branchId),
                    JdbcBranchAccessPolicyAdapter::mapPolicy);
            if (results.isEmpty()) {
                throw new BranchAccessPolicyNotFoundException();
            }
            return results.get(0);
        } catch (BranchAccessPolicyNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BranchAccessPolicyDataAccessException(
                    "Branch access policy could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public EffectiveBranchAccessPolicy update(
            UUID organizationId,
            UUID branchId,
            BranchAccessPaymentPolicyMode mode,
            long expectedVersion,
            UUID actorUserId,
            Instant occurredAt) {
        requireIdentifiers(organizationId, branchId);
        if (mode == null || expectedVersion < 0 || actorUserId == null || occurredAt == null) {
            throw new BranchAccessPolicyDataAccessException(
                    "Branch access policy update input is invalid.", null);
        }
        MapSqlParameterSource parameters = parameters(organizationId, branchId)
                .addValue("mode", mode.name())
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorUserId", actorUserId)
                .addValue("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
        try {
            List<UUID> lockedBranches = jdbcTemplate.query(
                    LOCK_ACTIVE_BRANCH_SQL,
                    parameters,
                    (resultSet, row) -> resultSet.getObject("id", UUID.class));
            if (lockedBranches.isEmpty()) {
                throw new BranchAccessPolicyNotFoundException();
            }

            List<UUID> updated = jdbcTemplate.query(
                    UPDATE_SQL,
                    parameters,
                    (resultSet, row) -> resultSet.getObject("branch_id", UUID.class));
            if (updated.isEmpty() && expectedVersion == 0) {
                updated = jdbcTemplate.query(
                        INSERT_SQL,
                        parameters,
                        (resultSet, row) -> resultSet.getObject("branch_id", UUID.class));
            }
            if (updated.isEmpty()) {
                throw new BranchAccessPolicyVersionConflictException();
            }
            return findForBranch(organizationId, branchId);
        } catch (BranchAccessPolicyNotFoundException
                | BranchAccessPolicyVersionConflictException
                | BranchAccessPolicyDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BranchAccessPolicyDataAccessException(
                    "Branch access policy could not be updated.", exception);
        }
    }

    private static EffectiveBranchAccessPolicy mapPolicy(
            ResultSet resultSet,
            int row) throws SQLException {
        return new EffectiveBranchAccessPolicy(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getBoolean("organization_default_requires_payment"),
                BranchAccessPaymentPolicyMode.valueOf(
                        resultSet.getString("branch_mode")),
                resultSet.getLong("version"));
    }

    private static MapSqlParameterSource parameters(UUID organizationId, UUID branchId) {
        return new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("branchId", branchId);
    }

    private static void requireIdentifiers(UUID organizationId, UUID branchId) {
        if (organizationId == null || branchId == null) {
            throw new BranchAccessPolicyDataAccessException(
                    "Organization and branch identifiers are required.", null);
        }
    }
}
