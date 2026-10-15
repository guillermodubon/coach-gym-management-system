package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class OperationsReportingSqlContractTest {

    @Test
    void incidentQueryUsesCanonicalRowsAndSeparateEventTimestamps() {
        String sql = normalize(JdbcIncidentReportingQuery.SUMMARY_BRANCH_SQL);

        assertThat(sql)
                .contains("from gym.incidents i")
                .contains("i.branch_id in (:branchids)")
                .contains("reported_at >= :frominclusive and reported_at < :toexclusive")
                .contains("resolved_at >= :frominclusive and resolved_at < :toexclusive")
                .contains("status in ('open', 'in_progress')")
                .doesNotContain("incident_status_history", "equipment_status_history",
                        "gym.equipment", "description", "resolution_notes", "select *");
    }

    @Test
    void maintenanceQueryUsesAuthoritativeBranchAndOverdueDateWithoutHistoryJoins() {
        String sql = normalize(JdbcMaintenanceReportingQuery.BRANCHES_FILTERED_SQL);

        assertThat(sql)
                .contains("from gym.maintenances m")
                .contains("m.branch_id in (:branchids)")
                .contains("m.status = 'scheduled' and m.scheduled_on < :operationaldate")
                .contains("group by m.branch_id")
                .doesNotContain("maintenance_status_history", "gym.equipment", "gym.incidents",
                        "technician_name", "provider_name", "select *");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
