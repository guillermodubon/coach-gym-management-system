package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class OperationalDashboardSqlContractTest {

    @Test
    void equipmentSqlCountsOnlyCurrentOperationalStates() {
        String sql = normalized(JdbcEquipmentDashboardQuery.SQL);
        assertThat(sql)
                .contains("status = 'available'")
                .contains("status = 'maintenance'")
                .contains("status = 'out_of_service'")
                .doesNotContain("status = 'retired'")
                .doesNotContain("select *");
    }

    @Test
    void incidentSqlCountsCriticalUnresolvedIncidents() {
        String sql = normalized(JdbcIncidentDashboardQuery.SQL);
        assertThat(sql)
                .contains("status = 'open'")
                .contains("status = 'in_progress'")
                .contains("priority = 'critical'")
                .contains("status in ('open', 'in_progress')");
    }

    @Test
    void maintenanceSqlDefinesOverdueAsScheduledBeforeOperationalDate() {
        String sql = normalized(JdbcMaintenanceDashboardQuery.SQL);
        assertThat(sql)
                .contains("status = 'scheduled'")
                .contains("status = 'in_progress'")
                .contains("scheduled_on < :operationaldate")
                .doesNotContain("status = 'completed'")
                .doesNotContain("status = 'cancelled'");
    }

    @Test
    void notificationSqlRequiresRecipientAndUnreadState() {
        String sql = normalized(JdbcDashboardNotificationQuery.SQL);
        assertThat(sql)
                .contains("recipient_user_id = :recipientuserid")
                .contains("read_at is null");
    }

    @Test
    void settingsSqlReadsAuthoritativeBusinessConfiguration() {
        String sql = normalized(JdbcDashboardSettingsQuery.SQL);
        assertThat(sql)
                .contains("membership_expiration_warning_days")
                .contains("default_currency")
                .contains("from gym.gym_settings")
                .contains("limit 1");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
