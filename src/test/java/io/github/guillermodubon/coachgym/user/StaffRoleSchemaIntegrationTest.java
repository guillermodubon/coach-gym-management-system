package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffRoleSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratedSchemaContainsOnlySupportedStaffRoles() {
        List<String> roleCodes = jdbcTemplate.queryForList(
                """
                select role_code
                  from gym.roles
                 order by role_code
                """,
                String.class);

        assertThat(roleCodes)
                .containsExactly("ADMIN", "RECEPTIONIST");
    }

    @Test
    void maintenanceRoleDoesNotExist() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from gym.roles
                 where role_code = 'MAINTENANCE'
                """,
                Integer.class);

        assertThat(count).isZero();
    }

    @Test
    void noUserRoleAssignmentReferencesAnUnsupportedRole() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from gym.user_roles user_role
                  join gym.roles role
                    on role.id = user_role.role_id
                 where role.role_code not in ('ADMIN', 'RECEPTIONIST')
                """,
                Integer.class);

        assertThat(count).isZero();
    }
}