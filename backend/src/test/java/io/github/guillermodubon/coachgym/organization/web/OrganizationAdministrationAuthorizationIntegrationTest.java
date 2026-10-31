package io.github.guillermodubon.coachgym.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real-session HTTP coverage for organization-wide administration authority. */
class OrganizationAdministrationAuthorizationIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final String BRANCH_ADMIN_USERNAME = "organization-branch-admin";
    private static final String BRANCH_ADMIN_PASSWORD = "Branch-admin-strong-password";
    private static final String BRANCH_ADMIN_EMAIL = "organization-branch-admin@example.test";
    private static final String DENIED_DETAIL = "You are not allowed to perform this operation.";
    private static final String CSRF_DETAIL = "The CSRF token is missing or invalid.";
    private static final String AUTHENTICATION_DETAIL = "Authentication is required.";
    private static final String UNAVAILABLE_DETAIL =
            "Administrative authority could not be verified.";

    @Autowired
    @MockitoSpyBean
    private StaffScopeQuery staffScopeQuery;

    private UUID branchAdminId;
    private MockHttpSession organizationAdminSession;
    private MockHttpSession branchAdminSession;
    private MockHttpSession receptionistSession;
    private UUID activeUnassignedBranchId;
    private UUID inactiveUnassignedBranchId;
    private OrganizationFixture organizationBeforeTest;

    @BeforeEach
    void provisionAuthorizationFixtures() throws Exception {
        organizationBeforeTest = readOrganizationFixture();
        branchAdminId = provisionUser(
                BRANCH_ADMIN_USERNAME,
                BRANCH_ADMIN_EMAIL,
                BRANCH_ADMIN_PASSWORD,
                "ADMIN");
        jdbcTemplate.update("""
                update gym.staff_scopes
                set scope_type = 'BRANCH', version = version + 1
                where user_id = ? and scope_type = 'ORGANIZATION'
                """, branchAdminId);

        assertThat(staffScopeQuery.findAuthorizationContext(branchAdminId))
                .get()
                .satisfies(context -> {
                    assertThat(context.branchAdmin()).isTrue();
                    assertThat(context.assignedTo(INITIAL_BRANCH_ID)).isTrue();
                });

        organizationAdminSession = loginAsAdmin();
        receptionistSession = loginAsReceptionist();
        branchAdminSession = login(BRANCH_ADMIN_USERNAME, BRANCH_ADMIN_PASSWORD);
        selectInitialBranch(branchAdminSession);

        activeUnassignedBranchId = createBranch(
                organizationAdminSession, uniqueCode("ACTIVE"));
        inactiveUnassignedBranchId = createBranch(
                organizationAdminSession, uniqueCode("INACTIVE"));
        deactivateBranch(organizationAdminSession, inactiveUnassignedBranchId);
    }

    @AfterEach
    void restoreOrganizationFixture() {
        if (organizationBeforeTest == null
                || organizationBeforeTest.equals(readOrganizationFixture())) {
            return;
        }
        jdbcTemplate.update("""
                update gym.organizations
                   set legal_name = ?, brand_name = ?, support_email = ?, support_phone = ?,
                       default_timezone = ?, default_currency = ?
                 where is_canonical = true
                """,
                organizationBeforeTest.legalName(),
                organizationBeforeTest.brandName(),
                organizationBeforeTest.supportEmail(),
                organizationBeforeTest.supportPhone(),
                organizationBeforeTest.defaultTimezone(),
                organizationBeforeTest.defaultCurrency());
    }

    @Test
    void organizationAdministratorCanUseAllFiveHttpMutations() throws Exception {
        long currentOrganizationVersion = organizationVersion();
        mockMvc.perform(put("/api/v1/organization")
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody(currentOrganizationVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(currentOrganizationVersion + 1));

        String createdCode = uniqueCode("CREATED");
        MvcResult created = mockMvc.perform(post("/api/v1/branches")
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBody(createdCode)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value(createdCode))
                .andReturn();
        UUID createdBranchId = responseId(created);
        assertThat(branchState(createdBranchId).get("status")).isEqualTo("ACTIVE");

        long activeVersion = branchVersion(activeUnassignedBranchId);
        mockMvc.perform(put("/api/v1/branches/{id}", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBranchBody("Updated " + uniqueCode("NAME"), activeVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(activeVersion + 1));

        long versionAfterUpdate = branchVersion(activeUnassignedBranchId);
        mockMvc.perform(post("/api/v1/branches/{id}/deactivate", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Integration lifecycle transition", versionAfterUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        long inactiveVersion = branchVersion(inactiveUnassignedBranchId);
        mockMvc.perform(post("/api/v1/branches/{id}/activate", inactiveUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Integration lifecycle transition", inactiveVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void branchAdministratorIsDeniedForAssignedAndUnassignedTargetsWithoutEffects()
            throws Exception {
        String attemptedCreateCode = uniqueCode("DENIED");
        BusinessSnapshot before = businessSnapshot();

        for (MockHttpServletRequestBuilder request : mutationRequests(
                branchAdminSession, attemptedCreateCode)) {
            expectProblem(request.with(csrf()), 403, "ACCESS_DENIED", DENIED_DETAIL);
        }

        assertNoBusinessEffects(before, attemptedCreateCode);
    }

    @Test
    void receptionistIsDeniedForAllFiveHttpMutationsWithoutEffects() throws Exception {
        String attemptedCreateCode = uniqueCode("DENIED");
        BusinessSnapshot before = businessSnapshot();

        for (MockHttpServletRequestBuilder request : mutationRequests(
                receptionistSession, attemptedCreateCode)) {
            expectProblem(request.with(csrf()), 403, "ACCESS_DENIED", DENIED_DETAIL);
        }

        assertNoBusinessEffects(before, attemptedCreateCode);
    }

    @Test
    void anonymousRequestsWithValidCsrfReceiveAuthenticationRequired() throws Exception {
        String attemptedCreateCode = uniqueCode("ANONYMOUS");
        BusinessSnapshot before = businessSnapshot();

        for (MockHttpServletRequestBuilder request : mutationRequests(null, attemptedCreateCode)) {
            expectProblem(request.with(csrf()), 401,
                    "AUTHENTICATION_REQUIRED", AUTHENTICATION_DETAIL);
        }

        assertNoBusinessEffects(before, attemptedCreateCode);
    }

    @Test
    void everyMutationRejectsMissingAndInvalidCsrfForOrganizationAdministrator()
            throws Exception {
        String attemptedCreateCode = uniqueCode("CSRF");
        BusinessSnapshot before = businessSnapshot();

        for (MockHttpServletRequestBuilder request : mutationRequests(
                organizationAdminSession, attemptedCreateCode)) {
            expectProblem(request, 403, "CSRF_TOKEN_INVALID", CSRF_DETAIL);
        }
        for (MockHttpServletRequestBuilder request : mutationRequests(
                organizationAdminSession, attemptedCreateCode)) {
            expectProblem(request.with(csrf().useInvalidToken()),
                    403, "CSRF_TOKEN_INVALID", CSRF_DETAIL);
        }

        assertNoBusinessEffects(before, attemptedCreateCode);
    }

    @Test
    void persistedAuthorityQueryFailureReturnsPrivateProblemWithoutEffects() throws Exception {
        BusinessSnapshot before = businessSnapshot();
        doThrow(new IllegalStateException("private authority query diagnostic"))
                .when(staffScopeQuery).findAuthorizationContext(adminId);

        MvcResult failure = expectProblem(
                mutationRequests(organizationAdminSession, uniqueCode("UNAVAILABLE"))
                        .getFirst().with(csrf()),
                500, "ORGANIZATION_ADMINISTRATION_UNAVAILABLE", UNAVAILABLE_DETAIL);
        assertThat(failure.getResponse().getContentAsString())
                .doesNotContain("private authority query diagnostic")
                .doesNotContain("IllegalStateException");

        assertThat(organizationState()).isEqualTo(before.organization());
        assertThat(branchState(INITIAL_BRANCH_ID)).isEqualTo(before.initialBranch());
        assertThat(branchState(activeUnassignedBranchId)).isEqualTo(before.activeBranch());
        assertThat(branchState(inactiveUnassignedBranchId)).isEqualTo(before.inactiveBranch());
        assertThat(businessAuditCount()).isEqualTo(before.businessAuditCount());
    }

    private List<MockHttpServletRequestBuilder> mutationRequests(
            MockHttpSession session,
            String attemptedCreateCode) {
        long orgVersion = organizationVersion();
        long assignedBranchVersion = branchVersion(INITIAL_BRANCH_ID);
        long activeBranchVersion = branchVersion(activeUnassignedBranchId);
        long inactiveBranchVersion = branchVersion(inactiveUnassignedBranchId);
        return List.of(
                withSession(put("/api/v1/organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody(orgVersion)), session),
                withSession(post("/api/v1/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBody(attemptedCreateCode)), session),
                withSession(put("/api/v1/branches/{id}", INITIAL_BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBranchBody("Assigned branch change", assignedBranchVersion)), session),
                withSession(post("/api/v1/branches/{id}/deactivate", activeUnassignedBranchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Denied branch transition", activeBranchVersion)), session),
                withSession(post("/api/v1/branches/{id}/activate", inactiveUnassignedBranchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Denied branch transition", inactiveBranchVersion)), session));
    }

    private static MockHttpServletRequestBuilder withSession(
            MockHttpServletRequestBuilder request,
            MockHttpSession session) {
        return session == null ? request : request.session(session);
    }

    private MvcResult expectProblem(
            MockHttpServletRequestBuilder request,
            int statusCode,
            String code,
            String detail) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(detail))
                .andReturn();
    }

    private UUID createBranch(MockHttpSession session, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/branches")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBody(code)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void deactivateBranch(MockHttpSession session, UUID branchId) throws Exception {
        mockMvc.perform(post("/api/v1/branches/{id}/deactivate", branchId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Integration fixture state", branchVersion(branchId))))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void selectInitialBranch(MockHttpSession session) throws Exception {
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"%s\"}".formatted(INITIAL_BRANCH_ID)))
                .andExpect(status().isOk());
    }

    private BusinessSnapshot businessSnapshot() {
        return new BusinessSnapshot(
                organizationState(),
                branchState(INITIAL_BRANCH_ID),
                branchState(activeUnassignedBranchId),
                branchState(inactiveUnassignedBranchId),
                businessAuditCount());
    }

    private void assertNoBusinessEffects(BusinessSnapshot before, String attemptedCreateCode) {
        assertThat(businessSnapshot()).isEqualTo(before);
        assertThat(branchCount(attemptedCreateCode)).isZero();
    }

    private Map<String, Object> organizationState() {
        return jdbcTemplate.queryForMap("""
                select legal_name, brand_name, version, updated_at
                from gym.organizations where is_canonical = true
                """);
    }

    private OrganizationFixture readOrganizationFixture() {
        return jdbcTemplate.queryForObject("""
                select legal_name, brand_name, support_email, support_phone,
                       default_timezone, default_currency
                from gym.organizations where is_canonical = true
                """, (resultSet, rowNumber) -> new OrganizationFixture(
                resultSet.getString("legal_name"),
                resultSet.getString("brand_name"),
                resultSet.getString("support_email"),
                resultSet.getString("support_phone"),
                resultSet.getString("default_timezone"),
                resultSet.getString("default_currency")));
    }

    private Map<String, Object> branchState(UUID branchId) {
        return jdbcTemplate.queryForMap("""
                select name, status, version, updated_at
                from gym.gym_branches where id = ?
                """, branchId);
    }

    private long organizationVersion() {
        return jdbcTemplate.queryForObject("""
                select version from gym.organizations where is_canonical = true
                """, Long.class);
    }

    private long branchVersion(UUID branchId) {
        return jdbcTemplate.queryForObject(
                "select version from gym.gym_branches where id = ?", Long.class, branchId);
    }

    private int branchCount(String code) {
        return jdbcTemplate.queryForObject(
                "select count(*) from gym.gym_branches where code = ?", Integer.class, code);
    }

    private long businessAuditCount() {
        return jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type in ('ORGANIZATION', 'GYM_BRANCH')
                """, Long.class);
    }

    private static String organizationBody(long version) {
        String name = "Integration " + uniqueCode("ORG");
        return """
                {
                  "legalName": "%s",
                  "brandName": "%s",
                  "supportEmail": "integration@example.test",
                  "supportPhone": "+50322223333",
                  "defaultTimezone": "America/El_Salvador",
                  "defaultCurrency": "USD",
                  "version": %d
                }
                """.formatted(name, name, version);
    }

    private static String createBranchBody(String code) {
        return """
                {
                  "code": "%s",
                  "name": "Integration %s",
                  "countryCode": "SV",
                  "timezone": "America/El_Salvador"
                }
                """.formatted(code, code);
    }

    private static String updateBranchBody(String name, long version) {
        return """
                {
                  "name": "%s",
                  "city": "San Salvador",
                  "countryCode": "SV",
                  "timezone": "America/El_Salvador",
                  "version": %d
                }
                """.formatted(name, version);
    }

    private static String statusBody(String reason, long version) {
        return """
                {"reason":"%s","version":%d}
                """.formatted(reason, version);
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10).toUpperCase(java.util.Locale.ROOT);
    }

    private record BusinessSnapshot(
            Map<String, Object> organization,
            Map<String, Object> initialBranch,
            Map<String, Object> activeBranch,
            Map<String, Object> inactiveBranch,
            long businessAuditCount) {}

    private record OrganizationFixture(
            String legalName,
            String brandName,
            String supportEmail,
            String supportPhone,
            String defaultTimezone,
            String defaultCurrency) {}
}
