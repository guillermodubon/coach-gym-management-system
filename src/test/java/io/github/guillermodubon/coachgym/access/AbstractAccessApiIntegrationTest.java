package io.github.guillermodubon.coachgym.access;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
abstract class AbstractAccessApiIntegrationTest {

    protected static final String ADMIN_USERNAME = "access-admin";
    protected static final String ADMIN_PASSWORD = "A-strong-password";
    protected static final String RECEPTIONIST_USERNAME = "access-receptionist";
    protected static final String RECEPTIONIST_PASSWORD = "R-strong-password";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

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
    void prepareAccessData() {
        jdbcTemplate.update("delete from gym.audit_entries where action_code='ACCESS_DENIED'");
        jdbcTemplate.update("delete from gym.access_records");
        provisionUser(ADMIN_USERNAME, "access-admin@example.com", ADMIN_PASSWORD, "ADMIN");
        provisionUser(RECEPTIONIST_USERNAME, "access-receptionist@example.com", RECEPTIONIST_PASSWORD, "RECEPTIONIST");
    }

    protected MockHttpSession loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAsReceptionist() throws Exception {
        return login(RECEPTIONIST_USERNAME, RECEPTIONIST_PASSWORD);
    }

    protected UUID userId(String username) {
        return jdbcTemplate.queryForObject(
                "select id from gym.users where lower(username)=lower(?)",
                UUID.class, username);
    }

    protected ClientFixture createClient(String status) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        UUID actorId = userId(ADMIN_USERNAME);
        jdbcTemplate.update("""
                insert into gym.clients
                    (id,first_name,last_name,email,phone,status,deactivated_at,
                     deactivated_by_user_id,deactivation_reason,created_by_user_id)
                values (?,?,?,?,?,?,
                    case when ?='INACTIVE' then current_timestamp else null end,
                    case when ?='INACTIVE' then ? else null end,
                    case when ?='INACTIVE' then 'Integration test' else null end,?)
                """, id, "Access", "Client", "access-" + suffix + "@example.com",
                "+5037" + Math.abs(id.hashCode() % 10000000), status,
                status, status, actorId, status, actorId);
        String code = jdbcTemplate.queryForObject(
                "select client_code from gym.clients where id=?", String.class, id);
        return new ClientFixture(id, code);
    }

    protected MembershipFixture createMembership(
            ClientFixture client, String status, LocalDate startsOn, LocalDate endsOn) {
        UUID actorId = userId(ADMIN_USERNAME);
        UUID planId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        String suffix = planId.toString().substring(0, 8);
        jdbcTemplate.update("""
                insert into gym.membership_plans
                    (id,name,description,duration_value,duration_unit,list_price,currency,
                     is_active,created_by_user_id)
                values (?,?,?,1,'MONTH',25.00,'USD',true,?)
                """, planId, "Access plan " + suffix, "Integration test plan", actorId);
        if ("CANCELLED".equals(status)) {
            jdbcTemplate.update("""
                    insert into gym.memberships
                        (id,client_id,status,cancelled_at,cancelled_by_user_id,
                         cancellation_reason,created_by_user_id)
                    values (?,?,'CANCELLED',current_timestamp,?,'Integration test',?)
                    """, membershipId, client.id(), actorId, actorId);
        } else {
            jdbcTemplate.update("""
                    insert into gym.memberships (id,client_id,status,created_by_user_id)
                    values (?,?,?,?)
                    """, membershipId, client.id(), status, actorId);
        }
        String planCode = jdbcTemplate.queryForObject(
                "select plan_code from gym.membership_plans where id=?", String.class, planId);
        jdbcTemplate.update("""
                insert into gym.membership_periods
                    (id,membership_id,period_number,period_source,membership_plan_id,
                     plan_code_snapshot,plan_name_snapshot,duration_value_snapshot,
                     duration_unit_snapshot,list_price,currency,discount_amount,final_price,
                     starts_on,base_ends_on,effective_ends_on,created_by_user_id)
                values (?,?,1,'INITIAL',?,?,?,1,'MONTH',25.00,'USD',0,25.00,?,?,?,?)
                """, periodId, membershipId, planId, planCode, "Access plan " + suffix,
                startsOn, endsOn, endsOn, actorId);
        String membershipCode = jdbcTemplate.queryForObject(
                "select membership_code from gym.memberships where id=?",
                String.class, membershipId);
        return new MembershipFixture(membershipId, membershipCode, periodId, client);
    }

    protected void createOpenFreeze(MembershipFixture membership,
            LocalDate startsOn, LocalDate endsOn) {
        jdbcTemplate.update("update gym.memberships set status='FROZEN' where id=?",
                membership.id());
        jdbcTemplate.update("""
                insert into gym.membership_freezes
                    (id,membership_id,membership_period_id,starts_on,planned_ends_on,
                     reason,created_by_user_id)
                values (?,?,?,?,?,'Integration test freeze',?)
                """, UUID.randomUUID(), membership.id(), membership.periodId(),
                startsOn, endsOn, userId(ADMIN_USERNAME));
    }

    protected MvcResult checkIn(
            MockHttpSession session,
            String identifier) throws Exception {

        return mockMvc.perform(
                        post("/api/v1/access/check-in")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identifier\":\"%s\"}"
                                                .formatted(identifier)))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    protected int countAccessRows() {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM gym.access_records
                        """,
                        Integer.class);

        return count == null ? 0 : count;
    }

    protected int countAccessAudits() {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM gym.audit_entries
                        WHERE action_code = 'ACCESS_DENIED'
                        """,
                        Integer.class);

        return count == null ? 0 : count;
    }

    protected Map<String, Object> accessRow(UUID id) {
        return jdbcTemplate.queryForMap(
                "select * from gym.access_records where id=?", id);
    }

    protected Map<String, Object> accessAudit(UUID accessRecordId) {
        return jdbcTemplate.queryForMap("""
                select * from gym.audit_entries
                where action_code='ACCESS_DENIED' and resource_id=?
                """, accessRecordId);
    }

    protected UUID insertAccessRow(
            String code,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID periodId,
            String decision,
            String reasonCode,
            Instant occurredAt,
            UUID actorId) {

        UUID id = UUID.randomUUID();

        String sql = """
            INSERT INTO gym.access_records (
                id,
                entered_code,
                client_id,
                client_code_snapshot,
                membership_id,
                membership_code_snapshot,
                membership_period_id,
                decision,
                reason_code,
                details,
                occurred_at,
                recorded_by_user_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(
                connection -> {
                    java.sql.PreparedStatement statement =
                            connection.prepareStatement(sql);

                    statement.setObject(
                            1,
                            id,
                            java.sql.Types.OTHER);

                    statement.setString(
                            2,
                            code);

                    setNullableUuid(
                            statement,
                            3,
                            clientId);

                    statement.setString(
                            4,
                            clientCode);

                    setNullableUuid(
                            statement,
                            5,
                            membershipId);

                    statement.setString(
                            6,
                            membershipCode);

                    setNullableUuid(
                            statement,
                            7,
                            periodId);

                    statement.setString(
                            8,
                            decision);

                    statement.setString(
                            9,
                            reasonCode);

                    statement.setString(
                            10,
                            "Integration test record");

                    statement.setTimestamp(
                            11,
                            java.sql.Timestamp.from(
                                    occurredAt));

                    setNullableUuid(
                            statement,
                            12,
                            actorId);

                    return statement;
                });

        return id;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void provisionUser(String username, String email,
            String password, String roleCode) {
        UUID id = jdbcTemplate.query("select id from gym.users where lower(username)=lower(?)",
                (rs, row) -> rs.getObject("id", UUID.class), username)
                .stream().findFirst().orElse(UUID.randomUUID());
        jdbcTemplate.update("""
                insert into gym.users
                    (id,username,email,password_hash,first_name,last_name,status)
                values (?,?,?,?,?,?,'ACTIVE')
                on conflict (id) do update set password_hash=excluded.password_hash,
                    status='ACTIVE'
                """, id, username, email, passwordEncoder.encode(password),
                "Access", "Staff");
        jdbcTemplate.update("delete from gym.user_roles where user_id=?", id);
        jdbcTemplate.update("""
                insert into gym.user_roles(user_id,role_id)
                select ?,id from gym.roles where role_code=?
                """, id, roleCode);
    }

    protected record ClientFixture(UUID id, String code) { }
    protected record MembershipFixture(
            UUID id, String code, UUID periodId, ClientFixture client) { }

    private static void setNullableUuid(
            java.sql.PreparedStatement statement,
            int parameterIndex,
            UUID value) throws java.sql.SQLException {

        if (value == null) {
            statement.setNull(
                    parameterIndex,
                    java.sql.Types.OTHER);
        } else {
            statement.setObject(
                    parameterIndex,
                    value,
                    java.sql.Types.OTHER);
        }
    }
}
