package io.github.guillermodubon.coachgym.maintenance;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentReportingApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void adminReportsIncidentWithGeneratedCodeAndInitialHistory()
            throws Exception {
        var session = loginAsAdmin();

        var result = mockMvc.perform(post("/api/v1/incidents")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId": "%s",
                                  "priority": "HIGH",
                                  "description": "  Motor controller fails intermittently.  ",
                                  "takeOutOfService": false,
                                  "equipmentVersion": null
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/incidents/[0-9a-f-]+")))
                .andExpect(jsonPath("$.incidentCode", matchesPattern("INC-[0-9]{6}")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.description")
                        .value("Motor controller fails intermittently."))
                .andExpect(jsonPath("$.reportedByUserId")
                        .value(adminId.toString()))
                .andReturn();

        UUID incidentId = responseId(result);
        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history where incident_id=?",
                Integer.class,
                incidentId);
        String initialStatus = jdbcTemplate.queryForObject(
                "select new_status from gym.incident_status_history where incident_id=?",
                String.class,
                incidentId);

        org.assertj.core.api.Assertions.assertThat(historyCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(initialStatus).isEqualTo("OPEN");
    }

    @Test
    void receptionistCanReportIncident() throws Exception {
        UUID incidentId = reportIncident(
                loginAsReceptionist(), "MEDIUM", false, null);
        String reporter = jdbcTemplate.queryForObject(
                "select reported_by_user_id::text from gym.incidents where id=?",
                String.class,
                incidentId);
        org.assertj.core.api.Assertions.assertThat(reporter)
                .isEqualTo(receptionistId.toString());
    }

    @Test
    void rejectsStructurallyInvalidReport() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId": "%s",
                                  "priority": "HIGH",
                                  "description": " ",
                                  "takeOutOfService": false
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isBadRequest());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.incidents", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }
}
