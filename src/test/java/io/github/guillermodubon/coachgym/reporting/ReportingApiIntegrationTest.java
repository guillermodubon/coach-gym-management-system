package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

class ReportingApiIntegrationTest extends AbstractDashboardApiIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    private static final String FROM = "2026-09-01";
    private static final String UNTIL = "2026-09-06";
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void anonymousRequestsCannotReachSummaryTrendOrComparisonRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reporting/financial-trend")
                        .param("scope", "ORGANIZATION")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reporting/branches/comparison")
                        .param("branchIds", INITIAL_BRANCH_ID.toString(), UUID.randomUUID().toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organizationAdministratorReceivesScopedSummaryContextAndSafeMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(loginAsAdmin())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.context.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.context.branchIds").isEmpty())
                .andExpect(jsonPath("$.context.fromInclusive").value(FROM))
                .andExpect(jsonPath("$.context.toExclusive").value(UNTIL))
                .andExpect(jsonPath("$.context.timezone").value("America/El_Salvador"))
                .andExpect(jsonPath("$.context.generatedAt").exists())
                .andExpect(jsonPath("$.metrics.financial").exists())
                .andExpect(jsonPath("$.metrics.access").exists())
                .andExpect(jsonPath("$.metrics.durableEmail").exists())
                .andExpect(jsonPath("$.metrics.notifications").doesNotExist())
                .andExpect(content().string(not(containsString("recipient"))));

        assertThat(meterRegistry.get("coachgym.reporting.requests")
                .tag("route", "reporting")
                .tag("outcome", "SUCCESS")
                .counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.get("coachgym.reporting.request.duration")
                .tag("route", "reporting")
                .tag("outcome", "SUCCESS")
                .timer().count()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void receptionistReceivesOnlyTheTwoApprovedBranchSummarySections() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(loginAsReceptionist())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.context.scope").value("ACTIVE_BRANCH"))
                .andExpect(jsonPath("$.context.branchIds[0]").value(INITIAL_BRANCH_ID.toString()))
                .andExpect(jsonPath("$.memberships").exists())
                .andExpect(jsonPath("$.access").exists())
                .andExpect(jsonPath("$.metrics").doesNotExist())
                .andExpect(jsonPath("$.notifications").doesNotExist());
    }

    @Test
    void branchAdministratorCanOnlyRequestAnAssignedBranchAndCannotRequestOrganization()
            throws Exception {
        var session = loginAsAdmin();
        jdbcTemplate.update("""
                update gym.staff_scopes set scope_type = 'BRANCH', version = version + 1
                where user_id = ?
                """, adminId);

        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(session)
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.scope").value("ACTIVE_BRANCH"));

        UUID unassignedBranchId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(session)
                        .param("scope", "SINGLE_BRANCH")
                        .param("branchIds", unassignedBranchId.toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Reporting data is not available for the requested actor or scope."))
                .andExpect(content().string(not(containsString("branch"))))
                .andExpect(content().string(not(containsString(unassignedBranchId.toString()))));

        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(session)
                        .param("scope", "ORGANIZATION")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationAdministratorMayFilterAnAuthorizedBranchButUnknownIdsFailClosed()
            throws Exception {
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(loginAsAdmin())
                        .param("scope", "SINGLE_BRANCH")
                        .param("branchIds", INITIAL_BRANCH_ID.toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.scope").value("SINGLE_BRANCH"))
                .andExpect(jsonPath("$.context.branchIds[0]").value(INITIAL_BRANCH_ID.toString()));

        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(loginAsAdmin())
                        .param("scope", "SINGLE_BRANCH")
                        .param("branchIds", UUID.randomUUID().toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isForbidden());
    }

    @Test
    void financialAndAccessTrendsAreBoundedAndRespectMetricRolePolicy() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/financial-trend")
                        .session(loginAsAdmin())
                        .param("scope", "ORGANIZATION")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL)
                        .param("granularity", "DAILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.granularity").value("DAILY"))
                .andExpect(jsonPath("$.points").isArray());

        mockMvc.perform(get("/api/v1/reporting/access-trend")
                        .session(loginAsReceptionist())
                        .param("scope", "ACTIVE_BRANCH")
                        .param("branchIds", INITIAL_BRANCH_ID.toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reporting/access-trend")
                        .session(loginAsAdmin())
                        .param("scope", "ORGANIZATION")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAILY"))
                .andExpect(jsonPath("$.points").isArray());

        mockMvc.perform(get("/api/v1/reporting/branches/comparison")
                        .session(loginAsReceptionist())
                        .param("branchIds", INITIAL_BRANCH_ID.toString(), UUID.randomUUID().toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesHalfOpenRangeGranularityScopeAndBranchCount() throws Exception {
        var adminSession = loginAsAdmin();
        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(adminSession)
                        .param("fromInclusive", UNTIL)
                        .param("toExclusive", FROM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORTING_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/reporting/financial-trend")
                        .session(adminSession)
                        .param("scope", "ORGANIZATION")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL)
                        .param("granularity", "HOURLY"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/reporting/summary")
                        .session(adminSession)
                        .param("scope", "UNKNOWN")
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isBadRequest());

        String[] tooManyBranches = java.util.stream.IntStream.range(0, 21)
                .mapToObj(ignored -> UUID.randomUUID().toString())
                .toArray(String[]::new);
        mockMvc.perform(get("/api/v1/reporting/branches/comparison")
                        .session(adminSession)
                        .param("branchIds", tooManyBranches)
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comparisonReturnsOnlyTheExplicitlyAuthorizedBranchSet() throws Exception {
        UUID otherBranch = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.gym_branches (
                    id, organization_id, code, name, country_code, timezone, status, version)
                select ?, id, ?, 'Comparison Test Branch', 'SV', 'America/El_Salvador', 'ACTIVE', 0
                from gym.organizations where is_canonical = true
                """, otherBranch, "CMP_" + otherBranch.toString().replace("-", "")
                .substring(0, 16).toUpperCase());

        mockMvc.perform(get("/api/v1/reporting/branches/comparison")
                        .session(loginAsAdmin())
                        .param("branchIds", INITIAL_BRANCH_ID.toString(), otherBranch.toString())
                        .param("fromInclusive", FROM)
                        .param("toExclusive", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches.length()").value(2))
                .andExpect(jsonPath("$.context.scope").value("AUTHORIZED_BRANCH_SET"))
                .andExpect(jsonPath("$.branches[*].metrics.financial").exists());
    }
}
