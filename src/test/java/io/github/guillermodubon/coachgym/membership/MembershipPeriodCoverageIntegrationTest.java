package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class MembershipPeriodCoverageIntegrationTest
        extends AbstractMembershipRenewalApiIntegrationTest {

    @Autowired
    private MembershipPeriodBranchCoverageQuery coverageQuery;

    @Test
    void creationCapturesSingleBranchCoverageAtRegistrationBranch()
            throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID initialBranchId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM gym.gym_branches
                WHERE is_initial_branch
                  AND status = 'ACTIVE'
                """,
                UUID.class);
        UUID clientId = createClient(session, uniqueValue("single-coverage-member") + "@example.com");
        UUID planId = createPlan(session, uniqueValue("Single Coverage Plan"), "25.00", "USD");

        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = periodId(membershipId, (short) 1);

        assertSnapshot(
                periodId,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(initialBranchId),
                0L);
        assertThat(coverageQuery.coversBranch(periodId, initialBranchId)).isTrue();
        assertThat(coverageQuery.coversBranch(periodId, UUID.randomUUID())).isFalse();
    }

    @Test
    void creationAndRenewalCaptureFiniteCoverageAndIgnoreLaterPlanChanges()
            throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID initialBranchId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM gym.gym_branches
                WHERE is_initial_branch
                  AND status = 'ACTIVE'
                """,
                UUID.class);
        UUID clientId = createClient(session, uniqueValue("coverage-member") + "@example.com");
        UUID planId = createPlan(session, uniqueValue("Coverage Snapshot Plan"), "25.00", "USD");
        UUID secondBranchId = createBranch(session);
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + initialBranchId + "\"}"))
                .andExpect(status().isOk());

        setPlanCoverage(planId, MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(initialBranchId, secondBranchId));
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID initialPeriodId = periodId(membershipId, (short) 1);

        assertSnapshot(initialPeriodId, MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(initialBranchId, secondBranchId), 1L);
        assertThat(coverageQuery.coversBranch(initialPeriodId, secondBranchId)).isTrue();

        // The purchased period remains exact even after the mutable plan changes.
        setPlanCoverage(planId, MembershipPlanBranchCoverageScope.ALL_BRANCHES, Set.of());
        assertThat(coverageQuery.coversBranch(initialPeriodId, secondBranchId)).isTrue();

        MvcResult renewal = renewMembership(
                session, membershipId, planId, null, "2026-10-01", 0)
                .andExpect(status().isOk())
                .andReturn();
        UUID renewalPeriodId = renewalPeriodId(renewal);
        Set<UUID> activeBranches = new HashSet<>(jdbcTemplate.queryForList(
                """
                SELECT branch.id
                FROM gym.gym_branches AS branch
                JOIN gym.organizations AS organization
                  ON organization.id = branch.organization_id
                WHERE branch.status = 'ACTIVE'
                  AND organization.is_canonical
                  AND organization.status = 'ACTIVE'
                """,
                UUID.class));

        assertSnapshot(renewalPeriodId, MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                activeBranches, 2L);
        assertThat(activeBranches).contains(initialBranchId, secondBranchId);
        assertThat(coverageQuery.coversBranch(renewalPeriodId, initialBranchId)).isTrue();
        assertThat(coverageQuery.coversBranch(renewalPeriodId, UUID.randomUUID())).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM gym.audit_entries
                WHERE resource_type = 'MEMBERSHIP_PERIOD'
                  AND resource_id = ?
                  AND action_code = 'MEMBERSHIP_PERIOD_COVERAGE_CAPTURED'
                  AND metadata ->> 'sourcePlanVersion' = '2'
                  AND metadata ->> 'coverageScope' = 'ALL_BRANCHES'
                """,
                Integer.class,
                renewalPeriodId)).isEqualTo(1);

        long branchVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM gym.gym_branches WHERE id = ?",
                Long.class,
                secondBranchId);
        mockMvc.perform(post("/api/v1/branches/{id}/deactivate", secondBranchId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Coverage integration fixture cleanup\","
                                + "\"version\":" + branchVersion + "}"))
                .andExpect(status().isOk());
        assertThat(coverageQuery.coversBranch(initialPeriodId, secondBranchId)).isTrue();
        assertThat(coverageQuery.coversBranch(renewalPeriodId, secondBranchId)).isTrue();
    }

    @Test
    void snapshotWriteFailureRollsBackMembershipPeriodAndAudit()
            throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueValue("coverage-rollback") + "@example.com");
        UUID planId = createPlan(session, uniqueValue("Coverage Rollback Plan"), "25.00", "USD");
        int periodsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.membership_periods", Integer.class);
        int snapshotsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.membership_period_coverage_snapshots", Integer.class);
        int membershipAuditsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.audit_entries WHERE action_code = 'MEMBERSHIP_CREATED'",
                Integer.class);
        int coverageAuditsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.audit_entries "
                        + "WHERE action_code = 'MEMBERSHIP_PERIOD_COVERAGE_CAPTURED'",
                Integer.class);

        jdbcTemplate.execute("""
                CREATE FUNCTION gym.reject_coverage_snapshot_test_insert()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'snapshot write rejected by integration fixture';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_test_reject_coverage_snapshot
                BEFORE INSERT ON gym.membership_period_coverage_snapshots
                FOR EACH ROW
                EXECUTE FUNCTION gym.reject_coverage_snapshot_test_insert()
                """);

        try {
            assertThatThrownBy(() -> mockMvc.perform(
                            post("/api/v1/memberships")
                                    .with(csrf())
                                    .session(session)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(membershipBody(
                                            clientId, planId, null, "2026-09-01")))
                    .andReturn())
                    .hasStackTraceContaining("snapshot write rejected");
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS trg_test_reject_coverage_snapshot "
                            + "ON gym.membership_period_coverage_snapshots");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS gym.reject_coverage_snapshot_test_insert()");
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.memberships WHERE client_id = ?",
                Integer.class,
                clientId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.membership_periods",
                Integer.class)).isEqualTo(periodsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.membership_period_coverage_snapshots",
                Integer.class)).isEqualTo(snapshotsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.audit_entries WHERE action_code = 'MEMBERSHIP_CREATED'",
                Integer.class)).isEqualTo(membershipAuditsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gym.audit_entries "
                        + "WHERE action_code = 'MEMBERSHIP_PERIOD_COVERAGE_CAPTURED'",
                Integer.class)).isEqualTo(coverageAuditsBefore);
    }

    private UUID createBranch(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/branches")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Coverage Integration Branch",
                                  "countryCode": "SV",
                                  "timezone": "America/El_Salvador"
                                }
                                """.formatted("CV-" + UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void setPlanCoverage(
            UUID planId,
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> branchIds) {
        jdbcTemplate.update(
                "DELETE FROM gym.membership_plan_branches WHERE membership_plan_id = ?",
                planId);
        List<Object[]> rows = branchIds.stream()
                .sorted()
                .map(branchId -> new Object[] {planId, branchId})
                .toList();
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO gym.membership_plan_branches (membership_plan_id, branch_id) "
                            + "VALUES (?, ?)",
                    rows);
        }
        jdbcTemplate.update(
                "UPDATE gym.membership_plans "
                        + "SET branch_coverage_scope = ?, version = version + 1 "
                        + "WHERE id = ?",
                scope.name(),
                planId);
    }

    private UUID periodId(UUID membershipId, short periodNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM gym.membership_periods WHERE membership_id = ? AND period_number = ?",
                UUID.class,
                membershipId,
                periodNumber);
    }

    private void assertSnapshot(
            UUID periodId,
            MembershipPlanBranchCoverageScope scope,
            Set<UUID> expectedBranches,
            long expectedPlanVersion) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT coverage_scope_snapshot FROM gym.membership_period_coverage_snapshots "
                        + "WHERE membership_period_id = ?",
                String.class,
                periodId)).isEqualTo(scope.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_plan_version FROM gym.membership_period_coverage_snapshots "
                        + "WHERE membership_period_id = ?",
                Long.class,
                periodId)).isEqualTo(expectedPlanVersion);
        assertThat(new HashSet<>(jdbcTemplate.queryForList(
                "SELECT branch_id FROM gym.membership_period_branch_coverage "
                        + "WHERE membership_period_id = ?",
                UUID.class,
                periodId))).isEqualTo(expectedBranches);
    }
}
