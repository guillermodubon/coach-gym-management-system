package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceQueryApiIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void adminAndReceptionistCanReadAndFilterMaintenance() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.UUID id = schedulePreventive(admin);
        MockHttpSession receptionist = loginAsReceptionist();

        mockMvc.perform(get("/api/v1/maintenances/{id}", id).session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(get("/api/v1/maintenances")
                        .session(receptionist)
                        .param("status", "SCHEDULED")
                        .param("maintenanceType", "PREVENTIVE")
                        .param("page", "0").param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/maintenances/{id}/history", id)
                        .session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
