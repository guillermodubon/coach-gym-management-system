package io.github.guillermodubon.coachgym.promotion;

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

@SpringBootTest(
        properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
abstract class AbstractPromotionApiIntegrationTest {

    protected static final String ADMIN_USERNAME =
            "coach-admin";

    protected static final String ADMIN_PASSWORD =
            "A-strong-password";

    protected static final String RECEPTIONIST_USERNAME =
            "promotion-front-desk";

    protected static final String RECEPTIONIST_PASSWORD =
            "R-strong-password";

    @ServiceConnection
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
    protected void provisionReceptionist() {
        jdbcTemplate.update(
                "delete from gym.users where username = ?",
                RECEPTIONIST_USERNAME);

        UUID receptionistId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                insert into gym.users (
                    id,
                    username,
                    email,
                    password_hash,
                    first_name,
                    last_name
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                receptionistId,
                RECEPTIONIST_USERNAME,
                "promotion-front-desk@coach-gym.local",
                passwordEncoder.encode(
                        RECEPTIONIST_PASSWORD),
                "Promotion",
                "Receptionist");

        jdbcTemplate.update(
                """
                insert into gym.user_roles (
                    user_id,
                    role_id
                )
                select ?, id
                from gym.roles
                where role_code = 'RECEPTIONIST'
                """,
                receptionistId);
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

    protected MvcResult createPercentagePromotion(
            MockHttpSession session,
            String name)
            throws Exception {

        return mockMvc.perform(
                        post("/api/v1/promotions")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        validPercentagePromotionBody(
                                                name)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected UUID promotionId(
            MvcResult result)
            throws Exception {

        return UUID.fromString(
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.id"));
    }

    protected static String validPercentagePromotionBody(
            String name) {

        return """
                {
                  "name": "%s",
                  "description": "Ten percent off selected plans.",
                  "discountType": "PERCENTAGE",
                  "discountValue": 10.00,
                  "currency": null,
                  "validFrom": "2026-09-01",
                  "validUntil": "2026-09-30"
                }
                """
                .formatted(name);
    }

    protected static String fixedAmountPromotionBody(
            String name) {

        return """
                {
                  "name": "%s",
                  "description": "Five dollars off selected plans.",
                  "discountType": "FIXED_AMOUNT",
                  "discountValue": 5.00,
                  "currency": "usd",
                  "validFrom": "2026-09-01",
                  "validUntil": "2026-09-30"
                }
                """
                .formatted(name);
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
}