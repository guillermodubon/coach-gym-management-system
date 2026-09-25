package io.github.guillermodubon.coachgym.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.plan.application.PlanCoverageApplicationService;
import io.github.guillermodubon.coachgym.plan.application.PlanSearchQuery;
import io.github.guillermodubon.coachgym.plan.application.PlanVersionConflictException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
class PlanApiIntegrationTest {

    private static final String ADMIN_USERNAME = "coach-admin";
    private static final String ADMIN_PASSWORD = "A-strong-password";
    private static final String RECEPTIONIST_USERNAME = "front-desk";
    private static final String RECEPTIONIST_PASSWORD = "R-strong-password";
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlanQuery planQuery;

    @Autowired
    private MembershipPlanBranchCoverageQuery branchCoverageQuery;

    @Autowired
    private MembershipPlanBranchEligibilityQuery branchEligibilityQuery;

    @Autowired
    private MembershipPlanSaleCoverageQuery saleCoverageQuery;

    @Autowired
    private PlanCoverageApplicationService branchCoverageService;

    @DynamicPropertySource
    static void configureBootstrapAdministrator(DynamicPropertyRegistry registry) {
        registry.add("coach-gym.bootstrap.admin.enabled", () -> true);
        registry.add("coach-gym.bootstrap.admin.username", () -> ADMIN_USERNAME);
        registry.add("coach-gym.bootstrap.admin.email", () -> "admin@coach-gym.local");
        registry.add("coach-gym.bootstrap.admin.password", () -> ADMIN_PASSWORD);
        registry.add("coach-gym.bootstrap.admin.first-name", () -> "Coach");
        registry.add("coach-gym.bootstrap.admin.last-name", () -> "Administrator");
    }

    @BeforeEach
    void provisionReceptionist() {
        List<UUID> existingIds = jdbcTemplate.queryForList(
                "select id from gym.users where username = ?",
                UUID.class,
                RECEPTIONIST_USERNAME);
        UUID userId = existingIds.isEmpty() ? UUID.randomUUID() : existingIds.getFirst();
        if (existingIds.isEmpty()) {
            jdbcTemplate.update("""
                    insert into gym.users (id, username, email, password_hash, first_name, last_name)
                    values (?, ?, ?, ?, ?, ?)
                    """, userId, RECEPTIONIST_USERNAME, "front-desk@coach-gym.local",
                    passwordEncoder.encode(RECEPTIONIST_PASSWORD), "Front", "Desk");
        } else {
            jdbcTemplate.update("""
                    update gym.users
                    set email = ?, password_hash = ?, first_name = ?, last_name = ?, status = 'ACTIVE'
                    where id = ?
                    """, "front-desk@coach-gym.local",
                    passwordEncoder.encode(RECEPTIONIST_PASSWORD), "Front", "Desk", userId);
        }
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = 'RECEPTIONIST'
                on conflict (user_id, role_id) do nothing
                """, userId);
        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, 'BRANCH', 0)
                on conflict (user_id) do update
                set scope_type = 'BRANCH', version = gym.staff_scopes.version + 1
                where gym.staff_scopes.scope_type <> 'BRANCH'
                """, userId);
        jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, version)
                values (?, ?, ?, 'ACTIVE', current_timestamp, 0)
                on conflict (user_id, branch_id) where status = 'ACTIVE' do nothing
                """, UUID.randomUUID(), userId, INITIAL_BRANCH_ID);
    }

    @Test
    void adminCreatesPlanWithGeneratedCodeAndAuditEntry() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Monthly Access",
                                  "description": "Unlimited gym access.",
                                  "durationValue": 1,
                                  "durationUnit": "MONTH",
                                  "listPrice": 25.00,
                                  "currency": "usd"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "http://localhost/api/v1/plans/[0-9a-f-]+")))
                .andExpect(jsonPath("$.planCode").value(org.hamcrest.Matchers.matchesPattern("PLAN-[0-9]{6}")))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.listPrice").value(25.00))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        UUID planId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_plans where id = ?", Integer.class, planId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries where resource_id = ? and action_code = 'PLAN_CREATED'",
                Integer.class,
                planId)).isEqualTo(1);
    }

    @Test
    void receptionistCanListPlansButCannotCreateOne() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Reception Plan")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans?active=true&page=0&size=25")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.name == 'Reception Plan')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(loginAsReceptionist())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Forbidden Plan")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanUpdateDeactivateAndReactivateWithVersionChecks() throws Exception {
        MockHttpSession session = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Lifecycle Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        MvcResult updated = mockMvc.perform(put("/api/v1/plans/{id}", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lifecycle Plan Updated",
                                  "description": null,
                                  "durationValue": 3,
                                  "durationUnit": "MONTH",
                                  "listPrice": 60.00,
                                  "currency": "USD",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lifecycle Plan Updated"))
                .andExpect(jsonPath("$.listPrice").value(60.00))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        mockMvc.perform(put("/api/v1/plans/{id}", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale Update",
                                  "durationValue": 3,
                                  "durationUnit": "MONTH",
                                  "listPrice": 60.00,
                                  "currency": "USD",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_VERSION_CONFLICT"));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/v1/plans/{id}/activate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(3));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries where resource_id = ? and action_code in ('PLAN_UPDATED', 'PLAN_DEACTIVATED', 'PLAN_REACTIVATED')",
                Integer.class,
                planId)).isEqualTo(3);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsUnauthenticatedAndInvalidPlanRequests() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/plans?page=-1").session(loginAsAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"durationValue\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String validPlanBody(String name) {
        return """
                {
                  "name": "%s",
                  "durationValue": 1,
                  "durationUnit": "MONTH",
                  "listPrice": 25.00,
                  "currency": "USD"
                }
                """.formatted(name);
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return selectInitialBranch(login(ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    private MockHttpSession loginAsReceptionist() throws Exception {
        return selectInitialBranch(login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD));
    }

    private MockHttpSession selectInitialBranch(MockHttpSession session) throws Exception {
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + INITIAL_BRANCH_ID + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void receptionistCannotUpdateOrChangePlanState() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Restricted Administration Plan")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        MockHttpSession receptionistSession = loginAsReceptionist();

        mockMvc.perform(put("/api/v1/plans/{id}", planId)
                        .with(csrf())
                        .session(receptionistSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Unauthorized Update",
                              "description": null,
                              "durationValue": 1,
                              "durationUnit": "MONTH",
                              "listPrice": 30.00,
                              "currency": "USD",
                              "version": 0
                            }
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(receptionistSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        /*
         * The plan remains active because the unauthorized deactivation was rejected.
         * Testing /activate also proves that the endpoint is protected before its
         * domain-state validation is executed.
         */
        mockMvc.perform(post("/api/v*/plans/{id}/activate", planId)
                .with(csrf())
                .session(receptionistSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
            .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select is_active from gym.membership_plans where id = ?",
                Boolean.class,
                planId)).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from gym.audit_entries
                where resource_id = ?
                  and action_code in (
                      'PLAN_UPDATED',
                      'PLAN_DEACTIVATED',
                      'PLAN_REACTIVATED'
                  )
                """,
                Integer.class,
                planId)).isZero();
    }

    @Test
    void branchCoverageHttpContractIsAuthorizedVersionedAuditedAndCsrfProtected()
            throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Coverage API Contract Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID secondBranchId = createActiveBranch("API-COVERAGE");

        mockMvc.perform(get("/api/v1/plans/{id}/branch-coverage", planId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/plans/{id}/branch-coverage", planId)
                        .session(loginAsReceptionist()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/plans/{id}/branch-coverage", planId)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SINGLE_BRANCH"))
                .andExpect(jsonPath("$.branchIds", contains(INITIAL_BRANCH_ID.toString())))
                .andExpect(jsonPath("$.version").value(0));

        String update = """
                {
                  "scope": "SELECTED_BRANCHES",
                  "branchIds": ["%s", "%s"],
                  "expectedVersion": 0
                }
                """.formatted(INITIAL_BRANCH_ID, secondBranchId);
        mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SELECTED_BRANCHES"))
                .andExpect(jsonPath("$.branchIds", hasSize(2)))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_VERSION_CONFLICT"));

        String auditMetadata = jdbcTemplate.queryForObject("""
                select metadata::text from gym.audit_entries
                where resource_id = ?
                  and action_code = 'MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED'
                """, String.class, planId);
        assertThat(auditMetadata)
                .contains("SELECTED_BRANCHES", "coveredBranchCount", "sourcePlanVersion")
                .doesNotContain(INITIAL_BRANCH_ID.toString(), secondBranchId.toString(), "branchIds");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_id = ?
                  and action_code = 'MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED'
                """, Integer.class, planId)).isEqualTo(1);
    }

    @Test
    void branchScopedAdministratorCannotReadOrManageOrganizationPlanCoverage()
            throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Branch Admin Restriction Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID adminId = jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?",
                UUID.class,
                ADMIN_USERNAME);
        Integer existingAssignments = jdbcTemplate.queryForObject("""
                select count(*)
                from gym.staff_branch_assignments
                where user_id = ? and branch_id = ? and status = 'ACTIVE'
                """, Integer.class, adminId, INITIAL_BRANCH_ID);
        UUID assignmentId = null;
        if (existingAssignments == 0) {
            assignmentId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into gym.staff_branch_assignments
                        (id, user_id, branch_id, status, assigned_at, version)
                    values (?, ?, ?, 'ACTIVE', current_timestamp, 0)
                    """, assignmentId, adminId, INITIAL_BRANCH_ID);
        }
        jdbcTemplate.update("""
                update gym.staff_scopes
                set scope_type = 'BRANCH', version = version + 1
                where user_id = ?
                """, adminId);

        try {
            mockMvc.perform(get("/api/v1/plans/{id}/branch-coverage", planId)
                            .session(adminSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLAN_OPERATION_FORBIDDEN"))
                    .andExpect(jsonPath("$.detail")
                            .value("The authenticated staff scope cannot perform this plan operation."));
            mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                            .with(csrf())
                            .session(adminSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"scope":"ALL_BRANCHES","branchIds":[],"expectedVersion":0}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLAN_OPERATION_FORBIDDEN"));
        } finally {
            jdbcTemplate.update("""
                    update gym.staff_scopes
                    set scope_type = 'ORGANIZATION', version = version + 1
                    where user_id = ?
                    """, adminId);
            if (assignmentId != null) {
                jdbcTemplate.update("""
                        update gym.staff_branch_assignments
                        set status = 'ENDED', ended_at = current_timestamp,
                            ended_by_user_id = ?, end_reason = 'Integration test fixture cleanup',
                            version = version + 1
                        where id = ? and status = 'ACTIVE'
                        """, adminId, assignmentId);
            }
        }
    }

    @Test
    void planCatalogUsesActiveOrServerAuthorizedBranchAndHidesOutOfScopeDetails()
            throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Scoped Catalog Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID secondBranchId = createActiveBranch("CATALOG-BRANCH");
        mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"SINGLE_BRANCH","branchIds":["%s"],"expectedVersion":0}
                                """.formatted(secondBranchId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/plans")
                        .session(adminSession)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", not(hasItem(planId.toString()))));
        mockMvc.perform(get("/api/v1/plans/{id}", planId)
                        .session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("The requested plan was not found."));

        mockMvc.perform(get("/api/v1/plans")
                        .session(adminSession)
                        .param("active", "true")
                        .param("branchId", secondBranchId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(planId.toString())));
        mockMvc.perform(get("/api/v1/plans")
                        .session(loginAsReceptionist())
                        .param("branchId", secondBranchId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void authenticatedAdminCannotCreatePlanWithoutCsrfToken() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(post("/api/v1/plans")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Missing CSRF Plan")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_plans where name = ?",
                Integer.class,
                "Missing CSRF Plan")).isZero();
    }

    @Test
    void deactivatedPlanIsExcludedFromActivePlansAndIncludedInInactivePlans()
            throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Inactive Filter Plan")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/plans")
                        .param("active", "true")
                        .param("name", "Inactive Filter Plan")
                        .param("page", "0")
                        .param("size", "25")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/plans")
                        .param("active", "false")
                        .param("name", "Inactive Filter Plan")
                        .param("page", "0")
                        .param("size", "25")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.items[0].active").value(false));
    }

    @Test
    void rejectsRepeatedPlanStateTransitions() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Repeated Transition Plan")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/api/v1/plans/{id}/activate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_STATE_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Plan is already active."));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_STATE_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Plan is already inactive."));

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from gym.audit_entries
                where resource_id = ?
                  and action_code = 'PLAN_DEACTIVATED'
                """,
                Integer.class,
                planId)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from gym.audit_entries
                where resource_id = ?
                  and action_code = 'PLAN_REACTIVATED'
                """,
                Integer.class,
                planId)).isZero();
    }

    @Test
    void activePlanQueryReturnsOnlyActivePlans() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Membership Dependency Plan")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        Optional<PlanDetails> activePlan = planQuery.findActiveById(planId);

        assertThat(activePlan)
                .isPresent()
                .get()
                .satisfies(plan -> {
                    assertThat(plan.id()).isEqualTo(planId);
                    assertThat(plan.active()).isTrue();
                    assertThat(plan.name()).isEqualTo("Membership Dependency Plan");
                });

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        assertThat(planQuery.findActiveById(planId)).isEmpty();
        assertThat(planQuery.findActiveById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getsPlanByIdAndReturnsNotFoundForUnknownPlan() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Plan Detail Query")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(get("/api/v1/plans/{id}", planId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.planCode")
                        .value(org.hamcrest.Matchers.matchesPattern("PLAN-[0-9]{6}")))
                .andExpect(jsonPath("$.name").value("Plan Detail Query"))
                .andExpect(jsonPath("$.durationValue").value(1))
                .andExpect(jsonPath("$.durationUnit").value("MONTH"))
                .andExpect(jsonPath("$.listPrice").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0));

        UUID unknownPlanId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/plans/{id}", unknownPlanId)
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("The requested plan was not found."));
    }

    @Test
    void sortsPlansUsingSeparateSortAndDirectionParameters() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("AAA Sorting Plan")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("ZZZ Sorting Plan")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans")
                        .param("name", "Sorting Plan")
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "name")
                        .param("direction", "desc")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].name").value("ZZZ Sorting Plan"))
                .andExpect(jsonPath("$.items[1].name").value("AAA Sorting Plan"));

        mockMvc.perform(get("/api/v1/plans")
                        .param("sort", "unsupported")
                        .param("direction", "asc")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_VALIDATION_FAILED"));
    }

    @Test
    void planCoverageIsPersistedVersionedAndQueriedWithoutRewritingHistory() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Coverage Integration Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID initialBranchId = jdbcTemplate.queryForObject(
                "select id from gym.gym_branches where is_initial_branch and status = 'ACTIVE'",
                UUID.class);
        UUID secondBranchId = createActiveBranch("COVERAGE-SECOND");
        UUID thirdBranchId = createActiveBranch("COVERAGE-THIRD");
        UUID adminId = jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?",
                UUID.class,
                ADMIN_USERNAME);
        AuthenticatedActor actor = new AuthenticatedActor(adminId, ADMIN_USERNAME);

        assertThat(branchCoverageQuery.findCoverage(planId))
                .hasValueSatisfying(coverage -> {
                    assertThat(coverage.scope())
                            .isEqualTo(MembershipPlanBranchCoverageScope.SINGLE_BRANCH);
                    assertThat(coverage.branchIds()).containsExactly(initialBranchId);
                    assertThat(coverage.version()).isZero();
                });
        assertThat(saleCoverageQuery.findForSale(planId, initialBranchId))
                .hasValueSatisfying(coverage -> {
                    assertThat(coverage.scope())
                            .isEqualTo(MembershipPlanBranchCoverageScope.SINGLE_BRANCH);
                    assertThat(coverage.coveredBranchIds()).containsExactly(initialBranchId);
                    assertThat(coverage.sourcePlanVersion()).isZero();
                });
        assertThat(saleCoverageQuery.findForSale(planId, secondBranchId)).isEmpty();

        Integer snapshotsBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_period_coverage_snapshots",
                Integer.class);

        inSecurityContext("ROLE_ADMIN", () -> {
            var selected = branchCoverageService.replaceCoverage(
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                            Set.of(initialBranchId, secondBranchId),
                            0),
                    actor);
            assertThat(selected.scope())
                    .isEqualTo(MembershipPlanBranchCoverageScope.SELECTED_BRANCHES);
            assertThat(selected.branchIds()).containsExactlyInAnyOrder(initialBranchId, secondBranchId);
            assertThat(selected.version()).isEqualTo(1);
        });

        assertThat(branchEligibilityQuery.isValidAtBranch(planId, initialBranchId)).isTrue();
        assertThat(branchEligibilityQuery.isValidAtBranch(planId, secondBranchId)).isTrue();
        assertThat(branchEligibilityQuery.isValidAtBranch(planId, thirdBranchId)).isFalse();
        assertThat(saleCoverageQuery.findForSale(planId, initialBranchId))
                .hasValueSatisfying(coverage -> {
                    assertThat(coverage.scope())
                            .isEqualTo(MembershipPlanBranchCoverageScope.SELECTED_BRANCHES);
                    assertThat(coverage.coveredBranchIds())
                            .containsExactlyInAnyOrder(initialBranchId, secondBranchId);
                    assertThat(coverage.sourcePlanVersion()).isEqualTo(1);
                });
        assertThat(saleCoverageQuery.findForSale(planId, thirdBranchId)).isEmpty();

        PlanSearchQuery branchSearch = PlanSearchQuery.from(
                true, "Coverage Integration Plan", 0, 10, "name", "asc");
        inSecurityContext("ROLE_ADMIN", () -> {
            assertThat(branchCoverageService.findVisiblePlans(
                    branchSearch, secondBranchId, actor).items())
                    .extracting(PlanDetails::id)
                    .contains(planId);
            assertThat(branchCoverageService.findVisiblePlans(
                    branchSearch, thirdBranchId, actor).items())
                    .extracting(PlanDetails::id)
                    .doesNotContain(planId);
        });

        inSecurityContext("ROLE_ADMIN", () -> {
            var allBranches = branchCoverageService.replaceCoverage(
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                            Set.of(),
                            1),
                    actor);
            assertThat(allBranches.branchIds()).isEmpty();
            assertThat(allBranches.version()).isEqualTo(2);
        });
        assertThat(branchEligibilityQuery.isValidAtBranch(planId, thirdBranchId)).isTrue();
        assertThat(saleCoverageQuery.findForSale(planId, secondBranchId))
                .hasValueSatisfying(coverage -> {
                    assertThat(coverage.scope())
                            .isEqualTo(MembershipPlanBranchCoverageScope.ALL_BRANCHES);
                    assertThat(coverage.coveredBranchIds())
                            .contains(initialBranchId, secondBranchId, thirdBranchId);
                    assertThat(coverage.sourcePlanVersion()).isEqualTo(2);
                });

        inSecurityContext("ROLE_ADMIN", () -> {
            assertThatThrownBy(() -> branchCoverageService.replaceCoverage(
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                            Set.of(initialBranchId),
                            1),
                    actor))
                    .isInstanceOf(PlanVersionConflictException.class);
        });
        assertThat(branchCoverageQuery.findCoverage(planId))
                .hasValueSatisfying(coverage -> {
                    assertThat(coverage.scope())
                            .isEqualTo(MembershipPlanBranchCoverageScope.ALL_BRANCHES);
                    assertThat(coverage.version()).isEqualTo(2);
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_period_coverage_snapshots",
                Integer.class)).isEqualTo(snapshotsBefore);
    }

    @Test
    void coverageMutationRequiresAdministratorAuthorityAtTheApplicationBoundary() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Protected Coverage Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID initialBranchId = jdbcTemplate.queryForObject(
                "select id from gym.gym_branches where is_initial_branch and status = 'ACTIVE'",
                UUID.class);
        UUID adminId = jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?",
                UUID.class,
                ADMIN_USERNAME);
        AuthenticatedActor actor = new AuthenticatedActor(adminId, ADMIN_USERNAME);

        SecurityContext original = SecurityContextHolder.getContext();
        SecurityContext noAdmin = SecurityContextHolder.createEmptyContext();
        noAdmin.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "front-desk",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))));
        SecurityContextHolder.setContext(noAdmin);
        try {
            assertThatThrownBy(() -> branchCoverageService.replaceCoverage(
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                            Set.of(initialBranchId),
                            0),
                    actor))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {
            SecurityContextHolder.setContext(original);
        }

        assertThat(branchCoverageQuery.findCoverage(planId))
                .hasValueSatisfying(coverage -> assertThat(coverage.version()).isZero());
    }

    @Test
    void concurrentCoverageReplacementAllowsOnlyOneUpdateForAnExpectedVersion() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MvcResult created = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlanBody("Concurrent Coverage Plan")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(
                JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID initialBranchId = jdbcTemplate.queryForObject(
                "select id from gym.gym_branches where is_initial_branch and status = 'ACTIVE'",
                UUID.class);
        UUID secondBranchId = createActiveBranch("COVERAGE-RACE");
        UUID adminId = jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?",
                UUID.class,
                ADMIN_USERNAME);
        AuthenticatedActor actor = new AuthenticatedActor(adminId, ADMIN_USERNAME);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = submitCoverageReplacement(
                    executor,
                    ready,
                    start,
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                            Set.of(initialBranchId),
                            0),
                    actor);
            Future<?> second = submitCoverageReplacement(
                    executor,
                    ready,
                    start,
                    planId,
                    new UpdateMembershipPlanBranchCoverageCommand(
                            MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                            Set.of(secondBranchId),
                            0),
                    actor);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0;
            int versionConflicts = 0;
            for (Future<?> update : List.of(first, second)) {
                try {
                    update.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof PlanVersionConflictException) {
                        versionConflicts++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(versionConflicts).isEqualTo(1);
            assertThat(branchCoverageQuery.findCoverage(planId))
                    .hasValueSatisfying(coverage -> {
                        assertThat(coverage.scope())
                                .isEqualTo(MembershipPlanBranchCoverageScope.SINGLE_BRANCH);
                        assertThat(coverage.version()).isEqualTo(1);
                        assertThat(coverage.branchIds()).hasSize(1)
                                .containsAnyOf(initialBranchId, secondBranchId);
                    });
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.membership_plan_branches where membership_plan_id = ?",
                    Integer.class,
                    planId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Future<?> submitCoverageReplacement(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            UUID planId,
            UpdateMembershipPlanBranchCoverageCommand command,
            AuthenticatedActor actor) {
        return executor.submit(() -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    ADMIN_USERNAME,
                    "",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            SecurityContextHolder.setContext(context);
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start coverage race.");
                }
                return branchCoverageService.replaceCoverage(planId, command, actor);
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        });
    }

    private UUID createActiveBranch(String prefix) {
        UUID branchId = UUID.randomUUID();
        String code = prefix + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, is_initial_branch, version)
                select ?, organization.id, ?, ?, organization.default_timezone,
                       'ACTIVE', false, 0
                from gym.organizations as organization
                where organization.is_canonical and organization.status = 'ACTIVE'
                """, branchId, code, prefix);
        return branchId;
    }

    private static void inSecurityContext(String role, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                ADMIN_USERNAME,
                "",
                List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

}
