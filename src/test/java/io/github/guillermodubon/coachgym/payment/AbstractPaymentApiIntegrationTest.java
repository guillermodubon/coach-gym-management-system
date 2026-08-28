package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
abstract class AbstractPaymentApiIntegrationTest {

    protected static final String ADMIN_USERNAME = "payment-admin";
    protected static final String ADMIN_PASSWORD = "A-strong-password";
    protected static final String RECEPTIONIST_USERNAME = "payment-receptionist";
    protected static final String RECEPTIONIST_PASSWORD = "R-strong-password";
    protected static final String MAINTENANCE_USERNAME = "payment-maintenance";
    protected static final String MAINTENANCE_PASSWORD = "M-strong-password";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("coach-gym.bootstrap.admin.enabled", () -> true);
        registry.add("coach-gym.bootstrap.admin.username", () -> ADMIN_USERNAME);
        registry.add("coach-gym.bootstrap.admin.email", () -> "payment-admin@coach-gym.local");
        registry.add("coach-gym.bootstrap.admin.password", () -> ADMIN_PASSWORD);
        registry.add("coach-gym.bootstrap.admin.first-name", () -> "Payment");
        registry.add("coach-gym.bootstrap.admin.last-name", () -> "Administrator");
    }

    @BeforeEach
    void provisionStaff() {
        provisionUser(RECEPTIONIST_USERNAME,
                "payment-receptionist@coach-gym.local",
                RECEPTIONIST_PASSWORD, "RECEPTIONIST");
        provisionUser(MAINTENANCE_USERNAME,
                "payment-maintenance@coach-gym.local",
                MAINTENANCE_PASSWORD, "MAINTENANCE");
    }

    protected MockHttpSession loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAsReceptionist() throws Exception {
        return login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD);
    }

    protected MockHttpSession loginAsMaintenance() throws Exception {
        return login(MAINTENANCE_USERNAME, MAINTENANCE_PASSWORD);
    }

    protected UUID createClient(MockHttpSession session, String email)
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/clients")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "firstName": "Payment",
                                          "lastName": "Client",
                                          "email": "%s",
                                          "phone": "+50370000000",
                                          "dateOfBirth": "1995-04-12"
                                        }
                                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        return responseId(result);
    }

    protected UUID createPlan(MockHttpSession session, String name,
            String price, String currency) throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/plans")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "%s",
                                          "description": "Payment integration test plan.",
                                          "durationValue": 1,
                                          "durationUnit": "MONTH",
                                          "listPrice": %s,
                                          "currency": "%s"
                                        }
                                        """.formatted(name, price, currency)))
                .andExpect(status().isCreated())
                .andReturn();

        return responseId(result);
    }

    protected UUID createPercentagePromotion(MockHttpSession session, String name)
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/promotions")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "%s",
                                          "description": "Payment integration test promotion.",
                                          "discountType": "PERCENTAGE",
                                          "discountValue": 10.00,
                                          "currency": null,
                                          "validFrom": "2026-09-01",
                                          "validUntil": "2026-09-30"
                                        }
                                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return responseId(result);
    }

    protected void addEligiblePlan(MockHttpSession session, UUID promotionId,
            long promotionVersion, UUID planId) throws Exception {

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.put(
                                        "/api/v1/promotions/{id}/eligible-plans",
                                        promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "planIds": ["%s"],
                                          "promotionVersion": %d
                                        }
                                        """.formatted(planId, promotionVersion)))
                .andExpect(status().isOk());
    }

    protected UUID createMembership(MockHttpSession session, UUID clientId,
            UUID planId, UUID promotionId, String startsOn) throws Exception {

        String promoValue = promotionId == null ? "null"
                : "\"" + promotionId + "\"";

        MvcResult result = mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "clientId": "%s",
                                          "membershipPlanId": "%s",
                                          "promotionId": %s,
                                          "startsOn": "%s"
                                        }
                                        """.formatted(clientId, planId, promoValue, startsOn)))
                .andExpect(status().isCreated())
                .andReturn();

        return responseId(result);
    }

    protected UUID getMembershipPeriodId(UUID membershipId) {
        return jdbcTemplate.queryForObject(
                """
                select id from gym.membership_periods
                where membership_id = ?
                order by period_number desc
                limit 1
                """,
                (rs, rn) -> rs.getObject("id", UUID.class),
                membershipId);
    }

    protected int countPaymentRows(UUID membershipId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where membership_id = ?",
                Integer.class, membershipId);
        return count == null ? 0 : count;
    }

    protected int countPaymentHistoryRows(UUID membershipId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from gym.payment_status_history h
                join gym.payments p on p.id = h.payment_id
                where p.membership_id = ?
                """,
                Integer.class, membershipId);
        return count == null ? 0 : count;
    }

    protected int countPaymentAuditRows(UUID membershipId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from gym.audit_entries
                where action_code = 'PAYMENT_REGISTERED'
                  and metadata->>'membershipId' = ?
                """,
                Integer.class, membershipId.toString());
        return count == null ? 0 : count;
    }

    protected static UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    protected static String uniqueEmail() {
        return "pay-" + UUID.randomUUID() + "@example.com";
    }

    protected static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private MockHttpSession login(String username, String password)
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "identifier": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void provisionUser(String username, String email,
            String password, String roleCode) {

        UUID existingId = jdbcTemplate.query(
                "select id from gym.users where lower(username) = lower(?)",
                (rs, rn) -> rs.getObject("id", UUID.class),
                username).stream().findFirst().orElse(null);

        UUID userId;
        if (existingId == null) {
            userId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    insert into gym.users (id,username,email,password_hash,first_name,last_name,status)
                    values (?,?,?,?,?,?,'ACTIVE')
                    """,
                    userId, username, email, passwordEncoder.encode(password),
                    "Payment", "Staff");
        } else {
            userId = existingId;
            jdbcTemplate.update(
                    """
                    update gym.users set email=?,password_hash=?,status='ACTIVE'
                    where id=?
                    """,
                    email, passwordEncoder.encode(password), userId);
        }

        jdbcTemplate.update("delete from gym.user_roles where user_id=?", userId);
        jdbcTemplate.update(
                """
                insert into gym.user_roles(user_id,role_id)
                select ?,id from gym.roles where role_code=?
                on conflict(user_id,role_id) do nothing
                """,
                userId, roleCode);
    }
}
