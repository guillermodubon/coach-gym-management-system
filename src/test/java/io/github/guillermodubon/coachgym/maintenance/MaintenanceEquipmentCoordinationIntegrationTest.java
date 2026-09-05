package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceEquipmentCoordinationIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void completionCanKeepEquipmentOutOfService() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.UUID id = schedulePreventive(admin);
        startMaintenance(admin, id, maintenanceVersion(id), equipmentVersion());
        completeMaintenance(
                admin, id, maintenanceVersion(id), equipmentVersion(), "OUT_OF_SERVICE");

        assertThat(maintenanceStatus(id)).isEqualTo("COMPLETED");
        assertThat(equipmentStatus()).isEqualTo("OUT_OF_SERVICE");
    }
}
