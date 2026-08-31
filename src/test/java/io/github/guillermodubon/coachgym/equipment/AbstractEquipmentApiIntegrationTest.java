package io.github.guillermodubon.coachgym.equipment;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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
abstract class AbstractEquipmentApiIntegrationTest {

    protected static final String ADMIN_USERNAME = "eq-admin";
    protected static final String ADMIN_PASSWORD = "A-strong-password";
    protected static final String MAINTENANCE_USERNAME = "eq-maintenance";
    protected static final String MAINTENANCE_PASSWORD = "M-strong-password";
    protected static final String RECEPTIONIST_USERNAME = "eq-receptionist";
    protected static final String RECEPTIONIST_PASSWORD = "R-strong-password";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    protected void provisionUser(String username, String email, String password, String roleCode) {
        UUID id = jdbcTemplate.query(
                "select id from gym.users where lower(username)=lower(?)",
                (rs, row) -> rs.getObject("id", UUID.class), username)
                .stream().findFirst().orElse(UUID.randomUUID());
        jdbcTemplate.update("""
                insert into gym.users (id,username,email,password_hash,first_name,last_name,status)
                values (?,?,?,?,?,?,'ACTIVE')
                on conflict (id) do update set password_hash=excluded.password_hash, status='ACTIVE'
                """, id, username, email, passwordEncoder.encode(password), "Equipment", "Staff");
        jdbcTemplate.update("delete from gym.user_roles where user_id=?", id);
        jdbcTemplate.update("""
                insert into gym.user_roles(user_id,role_id)
                select ?,id from gym.roles where role_code=?
                """, id, roleCode);
    }

    protected MockHttpSession loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAsMaintenance() throws Exception {
        return login(MAINTENANCE_USERNAME, MAINTENANCE_PASSWORD);
    }

    protected MockHttpSession loginAsReceptionist() throws Exception {
        return login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD);
    }

    protected UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    protected long responseVersion(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
