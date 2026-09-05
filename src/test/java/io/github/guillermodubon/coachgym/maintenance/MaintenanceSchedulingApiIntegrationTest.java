package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceSchedulingApiIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void adminSchedulesPreventiveMaintenanceWithGeneratedCodeAndHistory()
            throws Exception {
        UUIDHolder holder = new UUIDHolder(schedulePreventive(loginAsAdmin()));
        String code = jdbcTemplate.queryForObject(
                "select maintenance_code from gym.maintenances where id=?",
                String.class, holder.id);
        assertThat(code).matches("MNT-[0-9]{6}");
        assertThat(maintenanceStatus(holder.id)).isEqualTo("SCHEDULED");
        Integer history = jdbcTemplate.queryForObject(
                "select count(*) from gym.maintenance_status_history where maintenance_id=? and previous_status is null and new_status='SCHEDULED'",
                Integer.class, holder.id);
        assertThat(history).isEqualTo(1);
    }

    @Test
    void adminSchedulesCorrectiveMaintenanceLinkedToIncident() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.UUID incidentId = reportIncident(admin, "HIGH", false, null);
        java.util.UUID maintenanceId = scheduleCorrective(admin, incidentId);
        java.util.UUID stored = jdbcTemplate.queryForObject(
                "select incident_id from gym.maintenances where id=?",
                java.util.UUID.class, maintenanceId);
        assertThat(stored).isEqualTo(incidentId);
    }

    private record UUIDHolder(java.util.UUID id) {}
}
