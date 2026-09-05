package io.github.guillermodubon.coachgym.maintenance;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceIncidentLinkIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void rejectsCorrectiveMaintenanceWhenIncidentBelongsToAnotherEquipment()
            throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID originalEquipment = equipmentId;
        UUID incidentId = reportIncident(admin, "HIGH", false, null);
        UUID anotherEquipment = insertEquipment("AVAILABLE", 0L);

        mockMvc.perform(post("/api/v1/maintenances")
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "incidentId":"%s",
                                  "maintenanceType":"CORRECTIVE",
                                  "scheduledOn":"2026-09-10",
                                  "currency":"USD"
                                }
                                """.formatted(anotherEquipment, incidentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").exists());

        equipmentId = originalEquipment;
    }
}
