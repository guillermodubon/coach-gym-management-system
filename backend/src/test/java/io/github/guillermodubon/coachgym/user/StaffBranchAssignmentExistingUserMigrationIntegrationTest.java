package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class StaffBranchAssignmentExistingUserMigrationIntegrationTest {

    private static final UUID ADMIN_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID RECEPTIONIST_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000002");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Test
    void migratesExistingAdminAndReceptionistWithoutChangingAuthenticationRows() {
        String location = "filesystem:"
                + Path.of("src/main/resources/db/migration")
                        .toAbsolutePath()
                        .toString()
                        .replace('\\', '/');

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(location)
                .target("31")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = jdbcTemplate();
        insertUser(jdbcTemplate, ADMIN_ID, "migration-admin", "ADMIN");
        insertUser(jdbcTemplate, RECEPTIONIST_ID, "migration-receptionist", "RECEPTIONIST");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(location)
                .load()
                .migrate();

        Map<String, Object> adminScope = jdbcTemplate.queryForMap("""
                select scope_type, version
                from gym.staff_scopes
                where user_id = ?
                """, ADMIN_ID);
        assertThat(adminScope)
                .containsEntry("scope_type", "ORGANIZATION")
                .containsEntry("version", 0L);

        Map<String, Object> receptionistScope = jdbcTemplate.queryForMap("""
                select scope_type, version
                from gym.staff_scopes
                where user_id = ?
                """, RECEPTIONIST_ID);
        assertThat(receptionistScope)
                .containsEntry("scope_type", "BRANCH")
                .containsEntry("version", 0L);

        Map<String, Object> assignment = jdbcTemplate.queryForMap("""
                select branch_id, status, version, assigned_by_user_id
                from gym.staff_branch_assignments
                where user_id = ?
                """, RECEPTIONIST_ID);
        assertThat(assignment)
                .containsEntry("branch_id", UUID.fromString(
                        "7b0bf7d5-5184-43d2-8f9a-200000000002"))
                .containsEntry("status", "ACTIVE")
                .containsEntry("version", 0L)
                .containsEntry("assigned_by_user_id", null);

        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.users where id = ?", String.class, ADMIN_ID))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.users where id = ?", String.class, RECEPTIONIST_ID))
                .isEqualTo("ACTIVE");
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static void insertUser(
            JdbcTemplate jdbcTemplate, UUID id, String username, String roleCode) {
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, 'migration-hash', 'Migration', 'User', 'ACTIVE')
                """, id, username, username + "@example.com");
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id
                from gym.roles
                where role_code = ?
                """, id, roleCode);
    }
}
