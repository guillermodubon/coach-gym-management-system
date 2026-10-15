package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceRejectedOperationSideEffectsIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void staleMaintenanceVersionRollsBackEquipmentTransition() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.UUID id = schedulePreventive(admin);
        long equipmentVersionBefore = equipmentVersion();
        int historyBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class, equipmentId);

        mockMvc.perform(post("/api/v1/maintenances/{id}/start", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-09-10T14:00:00Z",
                                 "reason":"Attempt with stale order version.",
                                 "maintenanceVersion":99,"equipmentVersion":%d}
                                """.formatted(equipmentVersionBefore)))
                .andExpect(status().isConflict());

        assertThat(maintenanceStatus(id)).isEqualTo("SCHEDULED");
        assertThat(equipmentStatus()).isEqualTo("AVAILABLE");
        assertThat(equipmentVersion()).isEqualTo(equipmentVersionBefore);
        Integer historyAfter = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class, equipmentId);
        assertThat(historyAfter).isEqualTo(historyBefore);
    }
}
