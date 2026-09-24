package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicySchemaIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @BeforeEach
    void resetPolicyToMigrationDefault() {
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false,
                    updated_by_user_id = null,
                    version = 0
                where id = 1
                """);
    }

    @Test
    void policyColumnIsNonNullAndDefaultsToFalseWithoutChangingExistingSettings()
            {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                select is_nullable, column_default
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'gym_settings'
                  and column_name = 'require_confirmed_payment_for_access'
                """);

        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat(column.get("column_default").toString())
                .containsIgnoringCase("false");
        assertThat(jdbcTemplate.queryForObject(
                "select display_name from gym.gym_settings where id = 1",
                String.class)).isEqualTo("Coach Gym");
        assertThat(jdbcTemplate.queryForObject(
                "select require_confirmed_payment_for_access "
                        + "from gym.gym_settings where id = 1",
                Boolean.class)).isFalse();
    }

    @Test
    void accessReasonConstraintAcceptsPaymentAndBranchCoverageDenials() {
        String definition = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_access_records_reason_code'
                """, String.class);

        assertThat(definition)
                .contains("PAYMENT_REQUIRED", "MEMBERSHIP_NOT_VALID_AT_BRANCH");
    }
}
