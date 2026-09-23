package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffBranchAssignmentSchemaIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void currentFlywayChainContainsV32AndV33AndExpectedSchemaObjects() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'gym'
                  and table_name in ('staff_scopes', 'staff_branch_assignments')
                order by table_name
                """, String.class);
        assertThat(tables).containsExactly(
                "staff_branch_assignments", "staff_scopes");

        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '32'
                  and success = true
                """, Integer.class);
        assertThat(migrationCount).isEqualTo(1);

        Integer ownershipMigrationCount = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '33'
                  and success = true
                """, Integer.class);
        assertThat(ownershipMigrationCount).isEqualTo(1);

        Map<String, String> ownershipNullability = jdbcTemplate.query(
                """
                select table_name, is_nullable
                from information_schema.columns
                where table_schema = 'gym'
                  and ((table_name = 'clients'
                        and column_name = 'home_branch_id')
                    or (table_name = 'memberships'
                        and column_name = 'registered_at_branch_id')
                    or (table_name = 'membership_periods'
                        and column_name = 'registered_at_branch_id'))
                order by table_name
                """,
                resultSet -> {
                    Map<String, String> values = new java.util.LinkedHashMap<>();
                    while (resultSet.next()) {
                        values.put(
                                resultSet.getString("table_name"),
                                resultSet.getString("is_nullable"));
                    }
                    return values;
                });
        assertThat(ownershipNullability)
                .containsEntry("clients", "NO")
                .containsEntry("memberships", "NO")
                .containsEntry("membership_periods", "NO");

        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'staff_branch_assignments'
                """, String.class);
        assertThat(indexes).contains(
                "uq_staff_branch_assignments_active_user_branch",
                "idx_staff_branch_assignments_branch_active",
                "idx_staff_branch_assignments_user_history");

        Map<String, Object> initialBranch = jdbcTemplate.queryForMap("""
                select id, organization_id, status, is_initial_branch
                from gym.gym_branches
                where is_initial_branch
                """);
        assertThat(initialBranch)
                .containsEntry("id", INITIAL_BRANCH_ID)
                .containsEntry("organization_id", ORGANIZATION_ID)
                .containsEntry("status", "ACTIVE")
                .containsEntry("is_initial_branch", true);
    }

    @Test
    void scopeAndAssignmentLifecycleInvariantsAreDatabaseProtected() {
        UUID subjectId = insertUser("branch-subject", "RECEPTIONIST");
        UUID actorId = insertUser("organization-actor", "ADMIN");
        UUID scopeId = subjectId;
        UUID assignmentId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into gym.staff_scopes
                    (user_id, scope_type, granted_at, version)
                values (?, 'BRANCH', current_timestamp, 0)
                """, scopeId);
        jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, version)
                values (?, ?, ?, 'ACTIVE', current_timestamp, 0)
                """, assignmentId, subjectId, INITIAL_BRANCH_ID);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, version)
                values (?, ?, ?, 'ACTIVE', current_timestamp, 0)
                """, UUID.randomUUID(), subjectId, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, ended_at, version)
                values (?, ?, ?, 'ENDED', current_timestamp, current_timestamp, 0)
                """, UUID.randomUUID(), subjectId, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("""
                update gym.staff_branch_assignments
                set status = 'ENDED', ended_at = current_timestamp,
                    ended_by_user_id = ?, end_reason = 'staff moved', version = 1
                where id = ?
                """, actorId, assignmentId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.staff_branch_assignments
                set end_reason = 'tampered', version = 2
                where id = ?
                """, assignmentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.staff_branch_assignments where id = ?", assignmentId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void scopeRoleCompatibilityAndRestrictiveReferencesAreEnforced() {
        UUID receptionistId = insertUser("scope-receptionist", "RECEPTIONIST");
        UUID adminId = insertUser("scope-admin", "ADMIN");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, 'ORGANIZATION', 0)
                """, receptionistId))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, 'ORGANIZATION', 0)
                """, adminId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.users where id = ?", adminId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, version)
                values (?, ?, ?, 'ACTIVE', 0)
                """, UUID.randomUUID(), receptionistId, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertUser(String username, String roleCode) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, 'test-hash', 'Test', 'Staff', 'ACTIVE')
                """, userId, username, username + "@example.com");
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id
                from gym.roles
                where role_code = ?
                """, userId, roleCode);
        return userId;
    }
}
