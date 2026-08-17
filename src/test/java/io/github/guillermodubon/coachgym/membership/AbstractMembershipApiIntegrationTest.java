package io.github.guillermodubon.coachgym.membership;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
@SpringBootTest(
        properties =
                "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
abstract class AbstractMembershipApiIntegrationTest {

    protected static final String ADMIN_USERNAME =
            "coach-admin";

    protected static final String ADMIN_PASSWORD =
            "A-strong-password";

    protected static final String RECEPTIONIST_USERNAME =
            "membership-front-desk";

    protected static final String RECEPTIONIST_PASSWORD =
            "R-strong-password";

    protected static final String MAINTENANCE_USERNAME =
            "membership-maintenance";

    protected static final String MAINTENANCE_PASSWORD =
            "M-strong-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:17-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configureBootstrapAdministrator(
            DynamicPropertyRegistry registry) {

        registry.add(
                "coach-gym.bootstrap.admin.enabled",
                () -> true);

        registry.add(
                "coach-gym.bootstrap.admin.username",
                () -> ADMIN_USERNAME);

        registry.add(
                "coach-gym.bootstrap.admin.email",
                () -> "admin@coach-gym.local");

        registry.add(
                "coach-gym.bootstrap.admin.password",
                () -> ADMIN_PASSWORD);

        registry.add(
                "coach-gym.bootstrap.admin.first-name",
                () -> "Coach");

        registry.add(
                "coach-gym.bootstrap.admin.last-name",
                () -> "Administrator");
    }

    @BeforeEach
    void provisionAdditionalStaffAccounts() {
        provisionUser(
                RECEPTIONIST_USERNAME,
                "membership-front-desk@coach-gym.local",
                RECEPTIONIST_PASSWORD,
                "RECEPTIONIST");

        provisionUser(
                MAINTENANCE_USERNAME,
                "membership-maintenance@coach-gym.local",
                MAINTENANCE_PASSWORD,
                "MAINTENANCE");
    }

    protected MockHttpSession loginAsAdmin()
            throws Exception {

        return login(
                ADMIN_USERNAME,
                ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAsReceptionist()
            throws Exception {

        return login(
                RECEPTIONIST_USERNAME,
                RECEPTIONIST_PASSWORD);
    }

    protected MockHttpSession loginAsMaintenance()
            throws Exception {

        return login(
                MAINTENANCE_USERNAME,
                MAINTENANCE_PASSWORD);
    }

    protected UUID createClient(
            MockHttpSession session,
            String email)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/clients")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "firstName": "Membership",
                                                  "lastName": "Client",
                                                  "email": "%s",
                                                  "phone": "+50370000000",
                                                  "dateOfBirth": "1995-04-12"
                                                }
                                                """
                                                        .formatted(
                                                                email)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected UUID createPlan(
            MockHttpSession session,
            String name,
            String price,
            String currency)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/plans")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Plan for membership integration testing.",
                                                  "durationValue": 1,
                                                  "durationUnit": "MONTH",
                                                  "listPrice": %s,
                                                  "currency": "%s"
                                                }
                                                """
                                                        .formatted(
                                                                name,
                                                                price,
                                                                currency)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected UUID createPercentagePromotion(
            MockHttpSession session,
            String name)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/promotions")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Promotion for membership integration testing.",
                                                  "discountType": "PERCENTAGE",
                                                  "discountValue": 10.00,
                                                  "currency": null,
                                                  "validFrom": "2026-09-01",
                                                  "validUntil": "2026-09-30"
                                                }
                                                """
                                                        .formatted(
                                                                name)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected void replaceEligiblePlans(
            MockHttpSession session,
            UUID promotionId,
            long promotionVersion,
            UUID... planIds)
            throws Exception {

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.put(
                                        "/api/v1/promotions/{id}/eligible-plans",
                                        promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        eligiblePlansBody(
                                                promotionVersion,
                                                planIds)))
                .andExpect(status().isOk());
    }

    protected static UUID responseId(
            MvcResult result)
            throws Exception {

        return UUID.fromString(
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.id"));
    }

    protected static String uniqueValue(
            String prefix) {

        return prefix
                + "-"
                + UUID.randomUUID();
    }

    protected static String membershipBody(
            UUID clientId,
            UUID planId,
            UUID promotionId,
            String startsOn) {

        String promotionValue =
                promotionId == null
                        ? "null"
                        : "\""
                          + promotionId
                          + "\"";

        return """
                {
                  "clientId": "%s",
                  "membershipPlanId": "%s",
                  "promotionId": %s,
                  "startsOn": "%s"
                }
                """
                .formatted(
                        clientId,
                        planId,
                        promotionValue,
                        startsOn);
    }

    private MockHttpSession login(
            String username,
            String password)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "identifier": "%s",
                                                  "password": "%s"
                                                }
                                                """
                                                        .formatted(
                                                                username,
                                                                password)))
                        .andExpect(status().isNoContent())
                        .andReturn();

        return (MockHttpSession)
                result.getRequest()
                        .getSession(false);
    }

    private void provisionUser(
            String username,
            String email,
            String password,
            String roleCode) {

        UUID existingUserId =
                jdbcTemplate.query(
                                """
                                select id
                                from gym.users
                                where lower(username) = lower(?)
                                """,
                                (resultSet, rowNumber) ->
                                        resultSet.getObject(
                                                "id",
                                                UUID.class),
                                username)
                        .stream()
                        .findFirst()
                        .orElse(null);

        UUID userId;

        if (existingUserId == null) {
            userId =
                    UUID.randomUUID();

            jdbcTemplate.update(
                    """
                    insert into gym.users (
                        id,
                        username,
                        email,
                        password_hash,
                        first_name,
                        last_name,
                        status
                    )
                    values (?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    userId,
                    username,
                    email,
                    passwordEncoder.encode(password),
                    "Membership",
                    "Staff");
        } else {
            userId =
                    existingUserId;

            jdbcTemplate.update(
                    """
                    update gym.users
                    set email = ?,
                        password_hash = ?,
                        first_name = ?,
                        last_name = ?,
                        status = 'ACTIVE'
                    where id = ?
                    """,
                    email,
                    passwordEncoder.encode(password),
                    "Membership",
                    "Staff",
                    userId);
        }

        jdbcTemplate.update(
                """
                delete from gym.user_roles
                where user_id = ?
                """,
                userId);

        int roleAssignments =
                jdbcTemplate.update(
                        """
                        insert into gym.user_roles (
                            user_id,
                            role_id
                        )
                        select ?, id
                        from gym.roles
                        where role_code = ?
                        on conflict (user_id, role_id)
                        do nothing
                        """,
                        userId,
                        roleCode);

        if (roleAssignments == 0) {
            Integer roleCount =
                    jdbcTemplate.queryForObject(
                            """
                            select count(*)
                            from gym.user_roles user_role
                            join gym.roles role
                              on role.id = user_role.role_id
                            where user_role.user_id = ?
                              and role.role_code = ?
                            """,
                            Integer.class,
                            userId,
                            roleCode);

            if (roleCount == null
                    || roleCount == 0) {

                throw new IllegalStateException(
                        "System role "
                                + roleCode
                                + " was not found.");
            }
        }
    }

    private static String eligiblePlansBody(
            long promotionVersion,
            UUID... planIds) {

        String identifiers =
                java.util.Arrays.stream(planIds)
                        .map(
                                planId ->
                                        "\""
                                                + planId
                                                + "\"")
                        .collect(
                                java.util.stream.Collectors
                                        .joining(","));

        return """
                {
                  "planIds": [%s],
                  "promotionVersion": %d
                }
                """
                .formatted(
                        identifiers,
                        promotionVersion);
    }
}