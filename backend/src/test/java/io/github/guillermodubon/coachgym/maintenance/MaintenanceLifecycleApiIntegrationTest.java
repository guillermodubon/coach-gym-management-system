package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceLifecycleApiIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void adminStartsAndCompletesMaintenanceReturningEquipmentAvailable()
            throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.UUID id = schedulePreventive(admin);

        startMaintenance(admin, id, maintenanceVersion(id), equipmentVersion());
        assertThat(maintenanceStatus(id)).isEqualTo("IN_PROGRESS");
        assertThat(equipmentStatus()).isEqualTo("MAINTENANCE");

        completeMaintenance(admin, id, maintenanceVersion(id), equipmentVersion(), "AVAILABLE");
        assertThat(maintenanceStatus(id)).isEqualTo("COMPLETED");
        assertThat(equipmentStatus()).isEqualTo("AVAILABLE");
        Integer history = jdbcTemplate.queryForObject(
                "select count(*) from gym.maintenance_status_history where maintenance_id=?",
                Integer.class, id);
        assertThat(history).isEqualTo(3);
    }
}
