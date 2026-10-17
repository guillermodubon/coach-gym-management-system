package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentQueryApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void adminAndReceptionistCanReadIncidentAndHistory() throws Exception {
        var admin = loginAsAdmin();
        var incidentId = reportIncident(admin, "HIGH", false, null);

        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId)
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.equipmentCode").isNotEmpty());

        mockMvc.perform(get("/api/v1/incidents/{id}/history", incidentId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].newStatus").value("OPEN"));
    }

    @Test
    void listsWithAllowlistedFiltersAndPagination() throws Exception {
        var session = loginAsAdmin();
        reportIncident(session, "LOW", false, null);
        reportIncident(session, "CRITICAL", false, null);

        mockMvc.perform(get("/api/v1/incidents")
                        .session(session)
                        .param("priority", "CRITICAL")
                        .param("status", "OPEN")
                        .param("search", "drive belt")
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "REPORTED_AT")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .param("sort", "database_column"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_VALIDATION_FAILED"));
    }
}
