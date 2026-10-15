package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceSecurityApiIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/maintenances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCanReadButCannotSchedule() throws Exception {
        MockHttpSession receptionist = loginAsReceptionist();
        mockMvc.perform(get("/api/v1/maintenances").session(receptionist))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maintenances")
                        .session(receptionist).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"equipmentId":"%s","maintenanceType":"PREVENTIVE",
                                 "scheduledOn":"2026-09-10","currency":"USD"}
                                """.formatted(equipmentId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutationRequiresCsrf() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(post("/api/v1/maintenances")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"equipmentId":"%s","maintenanceType":"PREVENTIVE",
                                 "scheduledOn":"2026-09-10","currency":"USD"}
                                """.formatted(equipmentId)))
                .andExpect(status().isForbidden());
    }
}
