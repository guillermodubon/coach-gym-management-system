package io.github.guillermodubon.coachgym.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
class AuthenticationApiIntegrationTest {

    private static final String ADMIN_USERNAME = "coach-admin";
    private static final String ADMIN_PASSWORD = "A-strong-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configureBootstrapAdministrator(DynamicPropertyRegistry registry) {
        registry.add("coach-gym.bootstrap.admin.enabled", () -> true);
        registry.add("coach-gym.bootstrap.admin.username", () -> ADMIN_USERNAME);
        registry.add("coach-gym.bootstrap.admin.email", () -> "admin@coach-gym.local");
        registry.add("coach-gym.bootstrap.admin.password", () -> ADMIN_PASSWORD);
        registry.add("coach-gym.bootstrap.admin.first-name", () -> "Coach");
        registry.add("coach-gym.bootstrap.admin.last-name", () -> "Administrator");
    }

    @Test
    void createsTheConfiguredInitialAdministratorWithAnEncodedPassword() {
        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from gym.users where username = ?", String.class, ADMIN_USERNAME);

        assertThat(passwordHash)
                .isNotEqualTo(ADMIN_PASSWORD)
                .satisfies(hash -> assertThat(passwordEncoder.matches(ADMIN_PASSWORD, hash)).isTrue());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.user_roles ur join gym.roles r on r.id = ur.role_id "
                        + "join gym.users u on u.id = ur.user_id where u.username = ? and r.role_code = 'ADMIN'",
                Integer.class,
                ADMIN_USERNAME)).isEqualTo(1);
    }

    @Test
    void exposesCsrfTokenAndRequiresItForLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void authenticatesAnAdministratorAndPersistsTheSession() throws Exception {
        MvcResult result = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.fullName").value("Coach Administrator"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void rejectsInvalidAndInactiveCredentialsWithoutDisclosingTheirCause() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_USERNAME, "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));

        jdbcTemplate.update(
                "insert into gym.users (id, username, email, password_hash, first_name, last_name, status) "
                        + "values (?, ?, ?, ?, ?, ?, 'INACTIVE')",
                UUID.randomUUID(),
                "inactive-user",
                "inactive@coach-gym.local",
                passwordEncoder.encode(ADMIN_PASSWORD),
                "Inactive",
                "User");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("inactive-user", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));
    }

    @Test
    void requiresAuthenticationForCurrentUserAndInvalidatesTheSessionOnLogout() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        MvcResult loginResult = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    private MvcResult login(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(identifier, password)))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private static String loginBody(String identifier, String password) {
        return "{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}";
    }
}
