package io.github.guillermodubon.coachgym.reporting;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSchemaReadinessIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void finalAccessSchemaUsesPersistedDecisionColumns() {
        List<String> columns =
                jdbcTemplate.queryForList(
                        """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'gym'
                          and table_name = 'access_records'
                        """,
                        String.class);

        assertThat(columns)
                .contains(
                        "decision",
                        "occurred_at")
                .doesNotContain(
                        "result",
                        "checked_in_at");
    }

    @Test
    void onlyCurrentStaffRolesRemain() {
        List<String> roles = jdbcTemplate.queryForList(
                "select role_code from gym.roles order by role_code",
                String.class);

        assertThat(roles)
                .contains("ADMIN", "RECEPTIONIST")
                .doesNotContain("MAINTENANCE", "ROLE_MAINTENANCE");
    }

    @Test
    void authoritativeDashboardSettingsArePresent() {
        Integer warningDays = jdbcTemplate.queryForObject("""
                select membership_expiration_warning_days
                from gym.gym_settings
                order by id
                limit 1
                """, Integer.class);
        String currency = jdbcTemplate.queryForObject("""
                select default_currency
                from gym.gym_settings
                order by id
                limit 1
                """, String.class);

        assertThat(warningDays).isBetween(0, 90);
        assertThat(currency).matches("[A-Z]{3}");
    }
}
