package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodBranchCoverageStore;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC persistence for immutable, exact membership-period branch snapshots. */
@Repository
class JdbcMembershipPeriodBranchCoverageAdapter
        implements MembershipPeriodBranchCoverageStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcMembershipPeriodBranchCoverageAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public void capture(MembershipPeriodBranchCoverageDetails snapshot) {
        Objects.requireNonNull(snapshot, "Membership period coverage snapshot is required.");
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO gym.membership_period_coverage_snapshots (
                    membership_period_id,
                    coverage_scope_snapshot,
                    captured_at,
                    source_plan_version
                )
                VALUES (?, ?, ?, ?)
                """,
                snapshot.membershipPeriodId(),
                snapshot.scopeSnapshot().name(),
                OffsetDateTime.ofInstant(snapshot.capturedAt(), ZoneOffset.UTC),
                snapshot.sourcePlanVersion());
        if (inserted != 1) {
            throw new IllegalStateException(
                    "Membership period coverage snapshot was not persisted.");
        }

        List<Object[]> branchRows = snapshot.coveredBranchIds().stream()
                .map(branchId -> new Object[] {snapshot.membershipPeriodId(), branchId})
                .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO gym.membership_period_branch_coverage (
                    membership_period_id,
                    branch_id
                )
                VALUES (?, ?)
                """,
                branchRows);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean coversBranch(UUID membershipPeriodId, UUID branchId) {
        Objects.requireNonNull(membershipPeriodId, "Membership period ID is required.");
        Objects.requireNonNull(branchId, "Branch ID is required.");
        Boolean covered = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM gym.membership_period_branch_coverage
                    WHERE membership_period_id = ?
                      AND branch_id = ?
                )
                """,
                Boolean.class,
                membershipPeriodId,
                branchId);
        return Boolean.TRUE.equals(covered);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPeriodCoverageSummary> findCoverageSummary(
            UUID membershipPeriodId) {
        Objects.requireNonNull(membershipPeriodId, "Membership period ID is required.");
        List<MembershipPeriodCoverageSummary> summaries = jdbcTemplate.query(
                """
                SELECT snapshot.coverage_scope_snapshot,
                       COUNT(coverage.branch_id) AS covered_branch_count
                FROM gym.membership_period_coverage_snapshots AS snapshot
                LEFT JOIN gym.membership_period_branch_coverage AS coverage
                  ON coverage.membership_period_id = snapshot.membership_period_id
                WHERE snapshot.membership_period_id = ?
                GROUP BY snapshot.coverage_scope_snapshot
                """,
                (resultSet, rowNum) -> new MembershipPeriodCoverageSummary(
                        MembershipPlanBranchCoverageScope.valueOf(
                                resultSet.getString("coverage_scope_snapshot")),
                        Math.toIntExact(resultSet.getLong("covered_branch_count"))),
                membershipPeriodId);
        return summaries.stream().findFirst();
    }
}
