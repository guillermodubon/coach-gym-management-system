package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
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
class ClientApiIntegrationTest {

    private static final String ADMIN_USERNAME = "coach-admin";
    private static final String ADMIN_PASSWORD = "A-strong-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void registersClientWithEmergencyContactAndAuditEntry() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": " Ana ",
                                  "lastName": " Martínez ",
                                  "email": " ANA@example.com ",
                                  "phone": " +50370000000 ",
                                  "dateOfBirth": "1995-04-12",
                                  "emergencyContact": {
                                    "fullName": "Carlos Martínez",
                                    "relationship": "Brother",
                                    "phone": "+50371111111"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "http://localhost/api/v1/clients/[0-9a-f-]+")))
                .andExpect(jsonPath("$.clientCode").value(org.hamcrest.Matchers.matchesPattern("CLI-[0-9]{6}")))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emergencyContact.primary").value(true))
                .andReturn();

        UUID clientId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.emergency_contacts where client_id = ?", Integer.class, clientId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries where resource_id = ? and action_code = 'CLIENT_REGISTERED'",
                Integer.class,
                clientId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/clients/{id}", clientId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCode").value(org.hamcrest.Matchers.matchesPattern("CLI-[0-9]{6}")))
                .andExpect(jsonPath("$.emergencyContact.fullName").value("Carlos Martínez"));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        MockHttpSession session = login();
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Martínez",
                  "email": "duplicate@example.com",
                  "phone": "+50370000000"
                }
                """;

        mockMvc.perform(post("/api/v1/clients").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/clients").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("duplicate@", "DUPLICATE@")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CLIENT"));
    }

    @Test
    void rejectsMalformedClientAndUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = login();
        mockMvc.perform(post("/api/v1/clients").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Martínez\",\"phone\":\"+50370000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsNotFoundForUnknownClient() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}", UUID.randomUUID()).session(login()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + ADMIN_USERNAME + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
