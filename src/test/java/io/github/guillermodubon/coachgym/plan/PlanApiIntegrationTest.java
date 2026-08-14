package io.github.guillermodubon.coachgym.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
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
        jdbcTemplate.update("delete from gym.users where username = ?", RECEPTIONIST_USERNAME);
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users (id, username, email, password_hash, first_name, last_name)
                values (?, ?, ?, ?, ?, ?)
                """, userId, RECEPTIONIST_USERNAME, "front-desk@coach-gym.local",
                passwordEncoder.encode(RECEPTIONIST_PASSWORD), "Front", "Desk");
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = 'RECEPTIONIST'
                """, userId);
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
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    private MockHttpSession loginAsReceptionist() throws Exception {
        return login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD);
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
                        .value("Plan " + unknownPlanId + " was not found."));
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

}
