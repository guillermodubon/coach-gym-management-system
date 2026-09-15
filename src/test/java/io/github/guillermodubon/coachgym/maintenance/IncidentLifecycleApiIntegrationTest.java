package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentLifecycleApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void adminProgressesOpenIncidentToResolved() throws Exception {
        var session = loginAsAdmin();
        var incidentId = reportIncident(session, "HIGH", false, null);

        var started = mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Investigation started.","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        long version = responseVersion(started);
        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incidentId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolutionNotes":"Controller replaced and tested.",
                                  "version":%d
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes")
                        .value("Controller replaced and tested."))
                .andExpect(jsonPath("$.resolvedByUserId")
                        .value(adminId.toString()));

        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history where incident_id=?",
                Integer.class, incidentId);
        org.assertj.core.api.Assertions.assertThat(historyCount).isEqualTo(3);
    }

    @Test
    void adminChangesPriorityWithoutCreatingStatusHistory() throws Exception {
        var session = loginAsAdmin();
        var incidentId = reportIncident(session, "MEDIUM", false, null);

        mockMvc.perform(post("/api/v1/incidents/{id}/priority", incidentId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority":"CRITICAL",
                                  "reason":"Immediate safety concern.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history where incident_id=?",
                Integer.class, incidentId);
        org.assertj.core.api.Assertions.assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void rejectsDirectOpenToResolvedAndStaleVersion() throws Exception {
        var session = loginAsAdmin();
        var incidentId = reportIncident(session, "HIGH", false, null);

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incidentId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNotes":"Skip.","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCIDENT_STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Start.","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCIDENT_VERSION_CONFLICT"));
    }
}
