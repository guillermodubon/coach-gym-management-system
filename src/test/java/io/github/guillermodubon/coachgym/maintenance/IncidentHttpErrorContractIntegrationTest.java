package io.github.guillermodubon.coachgym.maintenance;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies stable Problem Details status and error-code contracts. */
class IncidentHttpErrorContractIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void unknownIncidentReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", UUID.randomUUID())
                        .session(loginAsAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
    }

    @Test
    void invalidFiltersReturnValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_VALIDATION_FAILED"));
    }

    @Test
    void retiredEquipmentReturnsConflictProblem() throws Exception {
        jdbcTemplate.update("""
                update gym.equipment
                set status='RETIRED', retired_at=current_timestamp,
                    retired_by_user_id=?, retirement_reason='End of life'
                where id=?
                """, adminId, equipmentId);

        mockMvc.perform(post("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "priority":"HIGH",
                                  "description":"Late incident.",
                                  "takeOutOfService":false
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_EQUIPMENT_RETIRED"));
    }

    @Test
    void staleIncidentVersionReturnsConflictProblem() throws Exception {
        UUID incidentId = reportIncident(loginAsAdmin(), "HIGH", false, null);

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Stale.","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_VERSION_CONFLICT"));
    }
}
