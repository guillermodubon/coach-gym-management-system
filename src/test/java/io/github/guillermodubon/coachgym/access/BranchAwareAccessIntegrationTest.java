package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "gym.access.duplicate-scan-window=PT30S")
class BranchAwareAccessIntegrationTest extends AbstractAccessApiIntegrationTest {

    private static final String QR_PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private AccessCredentialTokenProtector tokenProtector;

    @BeforeEach
    void clearQrFixtures() {
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false
                where id = 1
                """);
        jdbcTemplate.update("""
                update gym.branch_access_policy_overrides
                set policy_mode = 'INHERIT', updated_by_user_id = null, version = 0
                """);
        jdbcTemplate.execute("truncate table gym.access_records, "
                + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void manualAccessUsesTheCurrentPeriodCoverageSnapshot() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID secondaryBranchId = createBranch(session);
        ClientFixture coveredClient = createClient("ACTIVE");
        MembershipFixture coveredMembership = createMembership(
                coveredClient,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30),
                Set.of(initialBranchId(), secondaryBranchId));

        selectBranch(session, secondaryBranchId);
        MvcResult allowed = checkIn(session, coveredMembership.code());

        assertThat(allowed.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"")
                .contains("\"reasonCode\":\"ACCESS_ALLOWED\"");
        assertThat(accessRow(responseId(allowed)).get("branch_id"))
                .isEqualTo(secondaryBranchId);

        ClientFixture uncoveredClient = createClient("ACTIVE");
        MembershipFixture uncoveredMembership = createMembership(
                uncoveredClient,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30));
        MvcResult denied = checkIn(session, uncoveredMembership.code());

        assertThat(denied.getResponse().getContentAsString())
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"MEMBERSHIP_NOT_VALID_AT_BRANCH\"")
                .doesNotContain(initialBranchId().toString());
        UUID deniedRecordId = responseId(denied);
        assertThat(accessRow(deniedRecordId).get("branch_id"))
                .isEqualTo(secondaryBranchId);
        assertThat(accessAudit(deniedRecordId).get("branch_id"))
                .isEqualTo(secondaryBranchId.toString());
    }

    @Test
    void recentSuccessfulAccessAtAnotherBranchWinsOverCoverageDenial() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID secondaryBranchId = createBranch(session);
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembership(
                client,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30));

        selectBranch(session, initialBranchId());
        MvcResult first = checkIn(session, membership.code());
        assertThat(first.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"");

        selectBranch(session, secondaryBranchId);
        MvcResult duplicate = checkIn(session, membership.code());
        String response = duplicate.getResponse().getContentAsString();

        assertThat(response)
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"DUPLICATE_CHECK_IN\"")
                .doesNotContain(initialBranchId().toString());
        UUID duplicateRecordId = responseId(duplicate);
        assertThat(accessRow(duplicateRecordId).get("branch_id"))
                .isEqualTo(secondaryBranchId);
        assertThat(accessAudit(duplicateRecordId).get("branch_id"))
                .isEqualTo(secondaryBranchId.toString());
        assertThat(jdbcTemplate.queryForObject(
                "select metadata::text from gym.audit_entries "
                        + "where action_code='ACCESS_DENIED' and resource_id=?",
                String.class,
                duplicateRecordId))
                .doesNotContain(initialBranchId().toString());
    }

    @Test
    void qrAccessChecksCoverageAndRecordsOnlySafeCredentialReference() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID secondaryBranchId = createBranch(session);
        ClientFixture client = createClient("ACTIVE");
        createMembership(
                client,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30));
        insertActiveCredential(client.id());
        selectBranch(session, secondaryBranchId);

        MvcResult denied = qrCheckIn(session);
        String response = denied.getResponse().getContentAsString();

        assertThat(response)
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"MEMBERSHIP_NOT_VALID_AT_BRANCH\"")
                .doesNotContain(QR_PAYLOAD, tokenProtector.fingerprint(QR_PAYLOAD),
                        initialBranchId().toString());
        Map<String, Object> row = accessRow(responseId(denied));
        assertThat(row)
                .containsEntry("entered_code", "QR_CREDENTIAL")
                .containsEntry("identification_source", "QR_CREDENTIAL")
                .containsEntry("branch_id", secondaryBranchId);
        assertThat(row.get("access_credential_id")).isNotNull();
    }

    @Test
    void manualAndQrAccessUseEffectivePolicyForThePhysicalBranch() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID secondaryBranchId = createBranch(session);
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false
                where id = 1
                """);
        putBranchPolicyOverride(
                secondaryBranchId, "REQUIRED", 0);

        ClientFixture manualClient = createClient("ACTIVE");
        MembershipFixture manualMembership = createMembership(
                manualClient,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30),
                Set.of(secondaryBranchId));
        selectBranch(session, secondaryBranchId);
        MvcResult manualRequired = checkIn(session, manualMembership.code());
        assertThat(manualRequired.getResponse().getContentAsString())
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"PAYMENT_REQUIRED\"");

        ClientFixture qrClient = createClient("ACTIVE");
        createMembership(
                qrClient,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30),
                Set.of(secondaryBranchId));
        insertActiveCredential(qrClient.id());
        MvcResult qrRequired = qrCheckIn(session);
        assertThat(qrRequired.getResponse().getContentAsString())
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"PAYMENT_REQUIRED\"");

        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = true
                where id = 1
                """);
        putBranchPolicyOverride(secondaryBranchId, "NOT_REQUIRED", 1);

        MvcResult manualNotRequired = checkIn(session, manualMembership.code());
        assertThat(manualNotRequired.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"")
                .contains("\"reasonCode\":\"ACCESS_ALLOWED\"");
        MvcResult qrNotRequired = qrCheckIn(session);
        assertThat(qrNotRequired.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"")
                .contains("\"reasonCode\":\"ACCESS_ALLOWED\"");
    }

    @Test
    void concurrentCrossBranchQrScansHaveOneAllowedAndOneDuplicate() throws Exception {
        MockHttpSession branchOneSession = loginAsAdmin();
        UUID secondaryBranchId = createBranch(branchOneSession);
        ClientFixture client = createClient("ACTIVE");
        createMembership(
                client,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30),
                Set.of(initialBranchId(), secondaryBranchId));
        insertActiveCredential(client.id());
        selectBranch(branchOneSession, initialBranchId());

        MockHttpSession branchTwoSession = loginAsAdmin();
        selectBranch(branchTwoSession, secondaryBranchId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> branchOne = concurrentQrCheckIn(branchOneSession, ready, start);
            Callable<String> branchTwo = concurrentQrCheckIn(branchTwoSession, ready, start);
            Future<String> first = executor.submit(branchOne);
            Future<String> second = executor.submit(branchTwo);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var outcomes = java.util.List.of(
                    responseOutcome(first.get(15, TimeUnit.SECONDS)),
                    responseOutcome(second.get(15, TimeUnit.SECONDS)));
            assertThat(outcomes)
                    .containsExactlyInAnyOrder(
                            "ALLOWED:ACCESS_ALLOWED",
                            "DENIED:DUPLICATE_CHECK_IN");
            assertThat(countAccessRows()).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.access_records "
                            + "where decision='ALLOWED' and client_id=?",
                    Integer.class,
                    client.id())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reportsIndexedCoveragePolicyQueryPlansAndRepresentativeCheckInP95(
            TestReporter reporter) throws Exception {
        MockHttpSession session = loginAsAdminWithActiveBranch();
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembership(
                client,
                "ACTIVE",
                today().minusDays(1),
                today().plusDays(30));
        UUID branchId = initialBranchId();
        UUID organizationId = jdbcTemplate.queryForObject("""
                select organization_id
                from gym.gym_branches
                where id = ?
                """, UUID.class, branchId);
        UUID planId = jdbcTemplate.queryForObject("""
                select membership_plan_id
                from gym.membership_periods
                where id = ?
                """, UUID.class, membership.periodId());

        String periodCoveragePlan = String.join("\n", jdbcTemplate.queryForList("""
                explain (analyze, buffers, format text)
                select exists (
                    select 1
                    from gym.membership_period_branch_coverage
                    where membership_period_id = ?
                      and branch_id = ?
                )
                """, String.class, membership.periodId(), branchId));
        reporter.publishEntry("membership-period-coverage-query-plan", periodCoveragePlan);
        assertThat(periodCoveragePlan).contains("membership_period_branch_coverage");

        String planEligibilityPlan = String.join("\n", jdbcTemplate.queryForList("""
                explain (analyze, buffers, format text)
                select exists (
                    select 1
                    from gym.membership_plans as plan
                    join gym.gym_branches as branch on branch.id = ?
                    join gym.organizations as organization
                      on organization.id = branch.organization_id
                    where plan.id = ?
                      and plan.is_active
                      and branch.status = 'ACTIVE'
                      and organization.is_canonical
                      and organization.status = 'ACTIVE'
                      and (
                          plan.branch_coverage_scope = 'ALL_BRANCHES'
                          or exists (
                              select 1
                              from gym.membership_plan_branches as coverage
                              where coverage.membership_plan_id = plan.id
                                and coverage.branch_id = branch.id
                          )
                      )
                )
                """, String.class, branchId, planId));
        reporter.publishEntry("membership-plan-branch-eligibility-query-plan", planEligibilityPlan);
        assertThat(planEligibilityPlan).contains("membership_plans", "membership_plan_branches");

        String policyPlan = String.join("\n", jdbcTemplate.queryForList("""
                explain (analyze, buffers, format text)
                select organization.id, branch.id,
                       settings.require_confirmed_payment_for_access,
                       coalesce(override.policy_mode, 'INHERIT'),
                       coalesce(override.version, 0)
                from gym.organizations as organization
                join gym.gym_branches as branch
                  on branch.organization_id = organization.id
                join gym.gym_settings as settings on settings.id = 1
                left join gym.branch_access_policy_overrides as override
                  on override.branch_id = branch.id
                where organization.id = ?
                  and organization.is_canonical
                  and organization.status = 'ACTIVE'
                  and branch.id = ?
                  and branch.status = 'ACTIVE'
                """, String.class, organizationId, branchId));
        reporter.publishEntry("effective-branch-policy-query-plan", policyPlan);
        assertThat(policyPlan)
                .contains("organizations", "gym_branches", "gym_settings",
                        "branch_access_policy_overrides");

        List<Long> elapsedMillis = new ArrayList<>();
        for (int sample = 0; sample < 20; sample++) {
            long startedAt = System.nanoTime();
            checkIn(session, membership.code());
            elapsedMillis.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
        Collections.sort(elapsedMillis);
        long p95Millis = elapsedMillis.get((int) Math.ceil(elapsedMillis.size() * 0.95) - 1);
        reporter.publishEntry("branch-aware-check-in-p95",
                p95Millis + " ms; 20 HTTP check-ins against a persisted membership period");
        assertThat(elapsedMillis).allMatch(elapsed -> elapsed >= 0L);
        assertThat(p95Millis).isLessThan(5000L);
    }

    private Callable<String> concurrentQrCheckIn(
            MockHttpSession session,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start QR access race.");
            }
            return qrCheckIn(session).getResponse().getContentAsString();
        };
    }

    private MvcResult qrCheckIn(MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(QR_PAYLOAD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void insertActiveCredential(UUID clientId) {
        UUID credentialId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        jdbcTemplate.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, storage_key, content_type, size_bytes,
                     checksum_sha256, renderer_version, created_at, updated_at, version)
                values (?, ?, ?, ?, 'sha256-v1', 'v1', 'ACTIVE', ?, ?, ?,
                        'image/png', 128, ?, 'qr-v1', ?, ?, 0)
                """, credentialId, clientId,
                "CRED-" + credentialId.toString().substring(0, 8),
                tokenProtector.fingerprint(QR_PAYLOAD),
                java.sql.Timestamp.from(issuedAt), userId(ADMIN_USERNAME),
                "access-credentials/" + credentialId + ".png", "f".repeat(64),
                java.sql.Timestamp.from(issuedAt), java.sql.Timestamp.from(issuedAt));
    }

    private void putBranchPolicyOverride(UUID branchId, String mode, long version) {
        jdbcTemplate.update("""
                insert into gym.branch_access_policy_overrides
                    (branch_id, policy_mode, updated_by_user_id, version)
                values (?, ?, ?, ?)
                on conflict (branch_id) do update set
                    policy_mode = excluded.policy_mode,
                    updated_by_user_id = excluded.updated_by_user_id,
                    version = excluded.version
                """, branchId, mode, userId(ADMIN_USERNAME), version);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/El_Salvador"));
    }

    private static String responseOutcome(String response) {
        return JsonPath.read(response, "$.result") + ":"
                + JsonPath.read(response, "$.reasonCode");
    }

}
