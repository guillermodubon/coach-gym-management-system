package io.github.guillermodubon.coachgym.plan.infrastructure.persistence;

import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageValidationException;
import io.github.guillermodubon.coachgym.plan.MembershipPlanSaleCoverage;
import io.github.guillermodubon.coachgym.plan.UpdateMembershipPlanBranchCoverageCommand;
import io.github.guillermodubon.coachgym.plan.application.PlanNotFoundException;
import io.github.guillermodubon.coachgym.plan.application.PlanPage;
import io.github.guillermodubon.coachgym.plan.application.PlanSearchQuery;
import io.github.guillermodubon.coachgym.plan.application.PlanSortDirection;
import io.github.guillermodubon.coachgym.plan.application.PlanStateConflictException;
import io.github.guillermodubon.coachgym.plan.application.PlanStore;
import io.github.guillermodubon.coachgym.plan.application.PlanVersionConflictException;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PlanPersistenceAdapter implements PlanStore {

    private final PlanJpaRepository planRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    PlanPersistenceAdapter(
            PlanJpaRepository planRepository,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate) {
        this.planRepository = planRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public PlanDetails create(
            PlanDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = planRepository.saveAndFlush(
                MembershipPlanJpaEntity.create(definition, actor, occurredAt));
        initializeDefaultCoverage(plan.id());
        entityManager.refresh(plan);
        return plan.toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanPage findAll(PlanSearchQuery query) {
        Page<MembershipPlanJpaEntity> page = planRepository.search(
                query.active(),
                query.name(),
                PageRequest.of(query.page(), query.size(), toSort(query)));
        return new PlanPage(
                page.getContent().stream().map(MembershipPlanJpaEntity::toDetails).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanPage findAllForBranch(PlanSearchQuery query, UUID branchId) {
        Objects.requireNonNull(query, "Plan query is required.");
        Objects.requireNonNull(branchId, "Branch ID is required.");

        String eligibility = """
                EXISTS (
                    SELECT 1
                    FROM gym.gym_branches AS branch
                    JOIN gym.organizations AS organization
                      ON organization.id = branch.organization_id
                    WHERE branch.id = ?
                      AND branch.status = 'ACTIVE'
                      AND organization.is_canonical
                      AND organization.status = 'ACTIVE'
                      AND (
                          plan.branch_coverage_scope = 'ALL_BRANCHES'
                          OR EXISTS (
                              SELECT 1
                              FROM gym.membership_plan_branches AS coverage
                              WHERE coverage.membership_plan_id = plan.id
                                AND coverage.branch_id = branch.id
                          )
                      )
                )
                """;
        String fromAndWhere = " FROM gym.membership_plans AS plan WHERE "
                + eligibility + " AND (CAST(? AS BOOLEAN) IS NULL OR plan.is_active = CAST(? AS BOOLEAN))"
                + " AND (CAST(? AS VARCHAR) = '' OR lower(plan.name) LIKE lower(concat('%', ?, '%')))";

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + fromAndWhere,
                Long.class,
                branchId,
                query.active(), query.active(),
                query.name(), query.name());

        String orderColumn = switch (query.sortField()) {
            case NAME -> "plan.name";
            case CREATED_AT -> "plan.created_at";
            case UPDATED_AT -> "plan.updated_at";
        };
        String orderDirection = query.direction() == PlanSortDirection.ASC ? "ASC" : "DESC";
        long offset = (long) query.page() * query.size();
        String sql = "SELECT plan.*" + fromAndWhere
                + " ORDER BY " + orderColumn + " " + orderDirection
                + ", plan.id ASC LIMIT ? OFFSET ?";

        List<PlanDetails> items = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> toPlanDetails(resultSet),
                branchId,
                query.active(), query.active(),
                query.name(), query.name(),
                query.size(), offset);
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) totalElements / query.size());
        return new PlanPage(items, query.page(), query.size(), totalElements, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanDetails> findById(UUID id) {
        return planRepository.findById(id).map(MembershipPlanJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPlanBranchCoverageDetails> findBranchCoverage(UUID planId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        List<CoverageHeader> headers = jdbcTemplate.query(
                """
                SELECT branch_coverage_scope, version
                FROM gym.membership_plans
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new CoverageHeader(
                        MembershipPlanBranchCoverageScope.valueOf(
                                resultSet.getString("branch_coverage_scope")),
                        resultSet.getLong("version")),
                planId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        CoverageHeader header = headers.getFirst();
        Set<UUID> branchIds = jdbcTemplate.query(
                """
                SELECT branch_id
                FROM gym.membership_plan_branches
                WHERE membership_plan_id = ?
                ORDER BY branch_id
                """,
                (resultSet, rowNum) -> resultSet.getObject("branch_id", UUID.class),
                planId).stream().collect(Collectors.toCollection(java.util.TreeSet::new));
        return Optional.of(new MembershipPlanBranchCoverageDetails(
                planId,
                header.scope(),
                branchIds,
                header.version()));
    }

    @Override
    @Transactional
    public Optional<MembershipPlanSaleCoverage> findSaleCoverage(
            UUID planId,
            UUID registrationBranchId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        Objects.requireNonNull(registrationBranchId, "Registration branch ID is required.");

        List<CoverageHeader> headers = jdbcTemplate.query(
                """
                SELECT branch_coverage_scope, version
                FROM gym.membership_plans
                WHERE id = ?
                  AND is_active
                FOR SHARE
                """,
                (resultSet, rowNum) -> new CoverageHeader(
                        MembershipPlanBranchCoverageScope.valueOf(
                                resultSet.getString("branch_coverage_scope")),
                        resultSet.getLong("version")),
                planId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }

        CoverageHeader header = headers.getFirst();
        String branchQuery = header.scope() == MembershipPlanBranchCoverageScope.ALL_BRANCHES
                ? """
                    SELECT branch.id
                    FROM gym.gym_branches AS branch
                    JOIN gym.organizations AS organization
                      ON organization.id = branch.organization_id
                    WHERE branch.status = 'ACTIVE'
                      AND organization.is_canonical
                      AND organization.status = 'ACTIVE'
                    ORDER BY branch.id
                    FOR SHARE OF branch, organization
                    """
                : """
                    SELECT branch.id
                    FROM gym.membership_plan_branches AS coverage
                    JOIN gym.gym_branches AS branch
                      ON branch.id = coverage.branch_id
                    JOIN gym.organizations AS organization
                      ON organization.id = branch.organization_id
                    WHERE coverage.membership_plan_id = ?
                      AND branch.status = 'ACTIVE'
                      AND organization.is_canonical
                      AND organization.status = 'ACTIVE'
                    ORDER BY branch.id
                    FOR SHARE OF branch, organization
                    """;
        List<UUID> branchRows = header.scope() == MembershipPlanBranchCoverageScope.ALL_BRANCHES
                ? jdbcTemplate.query(
                        branchQuery,
                        (resultSet, rowNum) -> resultSet.getObject("id", UUID.class))
                : jdbcTemplate.query(
                        branchQuery,
                        (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                        planId);
        Set<UUID> branchIds = new java.util.TreeSet<>(branchRows);

        int branchCount = branchIds.size();
        boolean validCardinality = switch (header.scope()) {
            case SINGLE_BRANCH -> branchCount == 1;
            case SELECTED_BRANCHES -> branchCount >= 2;
            case ALL_BRANCHES -> branchCount >= 1;
        };
        if (!validCardinality || !branchIds.contains(registrationBranchId)) {
            return Optional.empty();
        }

        return Optional.of(new MembershipPlanSaleCoverage(
                planId,
                header.scope(),
                branchIds,
                header.version()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValidAtBranch(UUID planId, UUID branchId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        Objects.requireNonNull(branchId, "Branch ID is required.");
        Boolean valid = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM gym.membership_plans AS plan
                    JOIN gym.gym_branches AS branch
                      ON branch.id = ?
                    JOIN gym.organizations AS organization
                      ON organization.id = branch.organization_id
                    WHERE plan.id = ?
                      AND plan.is_active
                      AND branch.status = 'ACTIVE'
                      AND organization.is_canonical
                      AND organization.status = 'ACTIVE'
                      AND (
                          plan.branch_coverage_scope = 'ALL_BRANCHES'
                          OR EXISTS (
                              SELECT 1
                              FROM gym.membership_plan_branches AS coverage
                              WHERE coverage.membership_plan_id = plan.id
                                AND coverage.branch_id = branch.id
                          )
                      )
                )
                """,
                Boolean.class,
                branchId,
                planId);
        return Boolean.TRUE.equals(valid);
    }

    @Override
    @Transactional
    public MembershipPlanBranchCoverageDetails replaceBranchCoverage(
            UUID planId,
            UpdateMembershipPlanBranchCoverageCommand command,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(command, "Coverage command is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(occurredAt, "Coverage update time is required.");

        MembershipPlanJpaEntity plan = findEntity(planId);
        ensureExpectedVersion(plan, command.expectedVersion());
        validateActiveCanonicalBranches(command.branchIds());
        if (command.scope() == MembershipPlanBranchCoverageScope.ALL_BRANCHES) {
            requireAnyActiveCanonicalBranch();
        }

        plan.changeBranchCoverage(command.scope(), actor, occurredAt);
        flush(plan);

        jdbcTemplate.update(
                "DELETE FROM gym.membership_plan_branches WHERE membership_plan_id = ?",
                planId);
        for (UUID branchId : command.branchIds()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO gym.membership_plan_branches (membership_plan_id, branch_id)
                    VALUES (?, ?)
                    """,
                    planId,
                    branchId);
        }

        return findBranchCoverage(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    @Override
    @Transactional
    public PlanDetails update(
            UUID id,
            PlanDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = findEntity(id);
        ensureExpectedVersion(plan, expectedVersion);
        plan.update(definition, actor, occurredAt);
        flush(plan);
        return plan.toDetails();
    }

    @Override
    @Transactional
    public PlanDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = findEntity(id);
        ensureExpectedVersion(plan, expectedVersion);
        if (plan.active() == active) {
            String state = active ? "active" : "inactive";
            throw new PlanStateConflictException("Plan is already " + state + ".");
        }
        plan.changeActive(active, actor, occurredAt);
        flush(plan);
        return plan.toDetails();
    }

    private MembershipPlanJpaEntity findEntity(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    private void initializeDefaultCoverage(UUID planId) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO gym.membership_plan_branches (membership_plan_id, branch_id)
                SELECT ?, branch.id
                FROM gym.gym_branches AS branch
                JOIN gym.organizations AS organization
                  ON organization.id = branch.organization_id
                WHERE branch.status = 'ACTIVE'
                  AND organization.is_canonical
                  AND organization.status = 'ACTIVE'
                ORDER BY branch.is_initial_branch DESC, branch.id
                LIMIT 1
                """,
                planId);
        if (inserted != 1) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "An active canonical branch is required to create a plan.");
        }
    }

    private void validateActiveCanonicalBranches(Set<UUID> branchIds) {
        if (branchIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(branchIds.size(), "?"));
        String sql = """
                SELECT branch.id
                FROM gym.gym_branches AS branch
                JOIN gym.organizations AS organization
                  ON organization.id = branch.organization_id
                WHERE branch.id IN (%s)
                  AND branch.status = 'ACTIVE'
                  AND organization.is_canonical
                  AND organization.status = 'ACTIVE'
                ORDER BY branch.id
                FOR SHARE OF branch, organization
                """.formatted(placeholders);
        List<UUID> activeBranchIds = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                branchIds.toArray());
        if (!new HashSet<>(activeBranchIds).equals(branchIds)) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Coverage may reference only active branches in the canonical organization.");
        }
    }

    private void requireAnyActiveCanonicalBranch() {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM gym.gym_branches AS branch
                    JOIN gym.organizations AS organization
                      ON organization.id = branch.organization_id
                    WHERE branch.status = 'ACTIVE'
                      AND organization.is_canonical
                      AND organization.status = 'ACTIVE'
                )
                """,
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "ALL_BRANCHES coverage requires at least one active canonical branch.");
        }
    }

    private static PlanDetails toPlanDetails(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new PlanDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("plan_code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getInt("duration_value"),
                io.github.guillermodubon.coachgym.plan.DurationUnit.valueOf(
                        resultSet.getString("duration_unit")),
                resultSet.getBigDecimal("list_price"),
                resultSet.getString("currency").trim(),
                resultSet.getBoolean("is_active"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                resultSet.getLong("version"));
    }

    private record CoverageHeader(MembershipPlanBranchCoverageScope scope, long version) {
    }

    private static void ensureExpectedVersion(MembershipPlanJpaEntity plan, long expectedVersion) {
        if (expectedVersion < 0 || plan.version() != expectedVersion) {
            throw new PlanVersionConflictException();
        }
    }

    private void flush(MembershipPlanJpaEntity plan) {
        try {
            planRepository.saveAndFlush(plan);
            entityManager.refresh(plan);
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new PlanVersionConflictException();
        }
    }

    private static Sort toSort(PlanSearchQuery query) {
        String property = switch (query.sortField()) {
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
        Sort.Direction direction = query.direction() == PlanSortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDetails> findByIds(
            Set<UUID> planIds) {

        return planRepository.findAllById(planIds)
                .stream()
                .map(MembershipPlanJpaEntity::toDetails)
                .toList();
    }
}
