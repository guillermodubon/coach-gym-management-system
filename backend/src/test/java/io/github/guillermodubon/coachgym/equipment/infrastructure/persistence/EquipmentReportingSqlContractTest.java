package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class EquipmentReportingSqlContractTest {

    @Test
    void aggregatesCurrentPersistedStatusOncePerEquipmentAndFiltersBranchesInSql() {
        String sql = normalize(JdbcEquipmentReportingQuery.BRANCHES_FILTERED_SQL);

        assertThat(sql)
                .contains("from gym.equipment e")
                .contains("e.branch_id in (:branchids)")
                .contains("group by e.branch_id")
                .contains("count(*) filter (where e.status = 'out_of_service')")
                .contains("'retired'")
                .doesNotContain("equipment_status_history", "gym.incidents", "gym.maintenances",
                        "join", "select *", "serial_number", "manufacturer");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
