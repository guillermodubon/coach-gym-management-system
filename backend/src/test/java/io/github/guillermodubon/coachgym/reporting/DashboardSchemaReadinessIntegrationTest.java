package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.reporting.application.DashboardSettingsQuery;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSchemaReadinessIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Autowired
    private DashboardSettingsQuery settingsQuery;

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
    void accessDashboardDateWindowHasItsDedicatedMinimalIndex() {
        String indexDefinition = jdbcTemplate.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'access_records'
                  and indexname = 'idx_access_records_occurred_at_decision'
                """, String.class);

        assertThat(indexDefinition.toLowerCase(Locale.ROOT))
                .contains("(occurred_at desc, decision)")
                .doesNotContain("client_id", "membership_id");

        Integer duplicateDefinitionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'gym'
                  and indexdef = ?
                """, Integer.class, indexDefinition);
        assertThat(duplicateDefinitionCount).isEqualTo(1);
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
                from gym.organizations
                where is_canonical = true
                """, String.class);

        assertThat(warningDays).isBetween(0, 90);
        assertThat(currency).matches("[A-Z]{3}");
    }

    @Test
    void dashboardSettingsDelegateCurrencyToCanonicalOrganization() {
        String previousCurrency = jdbcTemplate.queryForObject("""
                select default_currency
                from gym.gym_settings
                order by id
                limit 1
                """, String.class);
        try {
            jdbcTemplate.update(
                    "update gym.gym_settings set default_currency = ?",
                    "EUR");

            assertThat(settingsQuery.load().currency()).isEqualTo("USD");
        } finally {
            jdbcTemplate.update(
                    "update gym.gym_settings set default_currency = ?",
                    previousCurrency);
        }
    }
}
