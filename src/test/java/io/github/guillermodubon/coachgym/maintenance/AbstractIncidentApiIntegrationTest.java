package io.github.guillermodubon.coachgym.maintenance;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
abstract class AbstractIncidentApiIntegrationTest {

    protected static final String ADMIN_USERNAME = "incident-admin";
    protected static final String ADMIN_PASSWORD = "Admin-strong-password";
    protected static final String RECEPTIONIST_USERNAME = "incident-receptionist";
    protected static final String RECEPTIONIST_PASSWORD = "Reception-strong-password";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected UUID adminId;
    protected UUID receptionistId;
    protected UUID categoryId;
    protected UUID equipmentId;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUpIncidentFixtures() {
        jdbcTemplate.update("delete from gym.audit_entries where resource_type in ('INCIDENT','EQUIPMENT')");
        jdbcTemplate.update("delete from gym.incident_status_history");
        jdbcTemplate.update("delete from gym.maintenances");
        jdbcTemplate.update("delete from gym.incidents");
        jdbcTemplate.update("delete from gym.equipment_status_history");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");

        adminId = provisionUser(
                ADMIN_USERNAME,
                "incident-admin@example.test",
                ADMIN_PASSWORD,
                "ADMIN");
        receptionistId = provisionUser(
                RECEPTIONIST_USERNAME,
                "incident-reception@example.test",
                RECEPTIONIST_PASSWORD,
                "RECEPTIONIST");

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.equipment_categories
                    (id, name, description, is_active, version)
                values (?, ?, ?, true, 0)
                """, categoryId, "Incident Test Category", "Integration fixtures");

        equipmentId = insertEquipment("AVAILABLE", 0L);
    }

    protected UUID insertEquipment(String equipmentStatus, long version) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.equipment
                    (id, equipment_category_id, name, status,
                     created_by_user_id, updated_by_user_id, version)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, categoryId, "Incident Test Treadmill", equipmentStatus,
                adminId, adminId, version);
        return id;
    }

    protected UUID provisionUser(
            String username,
            String email,
            String password,
            String roleCode) {
        UUID id = jdbcTemplate.query(
                "select id from gym.users where lower(username)=lower(?)",
                (rs, row) -> rs.getObject("id", UUID.class), username)
                .stream().findFirst().orElse(UUID.randomUUID());
        jdbcTemplate.update("""
                insert into gym.users
                    (id,username,email,password_hash,first_name,last_name,status)
                values (?,?,?,?,?,?,'ACTIVE')
                on conflict (id) do update set
                    password_hash=excluded.password_hash,
                    status='ACTIVE'
                """, id, username, email, passwordEncoder.encode(password),
                "Incident", "Staff");
        jdbcTemplate.update("delete from gym.user_roles where user_id=?", id);
        jdbcTemplate.update("""
                insert into gym.user_roles(user_id,role_id)
                select ?,id from gym.roles where role_code=?
                """, id, roleCode);
        return id;
    }

    protected MockHttpSession loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAsReceptionist() throws Exception {
        return login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD);
    }

    protected UUID reportIncident(
            MockHttpSession session,
            String priority,
            boolean takeOutOfService,
            Long equipmentVersion) throws Exception {
        String versionProperty = equipmentVersion == null
                ? "null" : equipmentVersion.toString();
        MvcResult result = mockMvc.perform(post("/api/v1/incidents")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId": "%s",
                                  "priority": "%s",
                                  "description": "Drive belt stops unexpectedly.",
                                  "takeOutOfService": %s,
                                  "equipmentVersion": %s
                                }
                                """.formatted(
                                equipmentId,
                                priority,
                                takeOutOfService,
                                versionProperty)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    protected long incidentVersion(UUID incidentId) {
        return jdbcTemplate.queryForObject(
                "select version from gym.incidents where id=?",
                Long.class,
                incidentId);
    }

    protected UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    protected long responseVersion(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.version"))
                .longValue();
    }

    private MockHttpSession login(String username, String password)
            throws Exception {
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
}
