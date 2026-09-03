package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the complete ADMIN and RECEPTIONIST incident authorization matrix. */
class IncidentAuthorizationMatrixApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void adminCanUseEveryIncidentOperation() throws Exception {
        var admin = loginAsAdmin();
        var incidentId = reportIncident(admin, "HIGH", false, null);

        mockMvc.perform(get("/api/v1/incidents").session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId).session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/{id}/history", incidentId).session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/incidents/{id}/priority", incidentId)
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority":"CRITICAL",
                                  "reason":"Immediate safety risk.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Investigation started.","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incidentId)
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolutionNotes":"Issue resolved and verified.",
                                  "version":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void receptionistCanReportAndReadButCannotManageLifecycle() throws Exception {
        var receptionist = loginAsReceptionist();
        var incidentId = reportIncident(receptionist, "MEDIUM", false, null);

        mockMvc.perform(get("/api/v1/incidents").session(receptionist))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId)
                        .session(receptionist))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/{id}/history", incidentId)
                        .session(receptionist))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(receptionist)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Forbidden.","version":0}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/incidents/{id}/priority", incidentId)
                        .session(receptionist)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority":"CRITICAL",
                                  "reason":"Forbidden.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incidentId)
                        .session(receptionist)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNotes":"Forbidden.","version":0}
                                """))
                .andExpect(status().isForbidden());
    }
}
