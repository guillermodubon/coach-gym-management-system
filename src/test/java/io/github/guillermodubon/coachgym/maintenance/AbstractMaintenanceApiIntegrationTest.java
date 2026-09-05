package io.github.guillermodubon.coachgym.maintenance;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractMaintenanceApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    protected UUID schedulePreventive(MockHttpSession session) throws Exception {
        return schedule(session, null, "PREVENTIVE");
    }

    protected UUID scheduleCorrective(MockHttpSession session, UUID incidentId)
            throws Exception {
        return schedule(session, incidentId, "CORRECTIVE");
    }

    protected UUID schedule(
            MockHttpSession session,
            UUID incidentId,
            String type) throws Exception {
        String incidentProperty = incidentId == null
                ? "null" : "\"" + incidentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/maintenances")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "incidentId":%s,
                                  "maintenanceType":"%s",
                                  "scheduledOn":"2026-09-10",
                                  "providerName":"Coach Gym Service",
                                  "technicianName":"Technical Staff",
                                  "estimatedCost":125.00,
                                  "currency":"USD",
                                  "notes":"Integration maintenance fixture",
                                  "assignedToUserId":null
                                }
                                """.formatted(equipmentId, incidentProperty, type)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    protected MvcResult startMaintenance(
            MockHttpSession session,
            UUID maintenanceId,
            long maintenanceVersion,
            long equipmentVersion) throws Exception {
        return mockMvc.perform(post("/api/v1/maintenances/{id}/start", maintenanceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startedAt":"2026-09-10T14:00:00Z",
                                  "reason":"Scheduled technical work started.",
                                  "maintenanceVersion":%d,
                                  "equipmentVersion":%d
                                }
                                """.formatted(maintenanceVersion, equipmentVersion)))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected MvcResult completeMaintenance(
            MockHttpSession session,
            UUID maintenanceId,
            long maintenanceVersion,
            long equipmentVersion,
            String outcome) throws Exception {
        return mockMvc.perform(post("/api/v1/maintenances/{id}/complete", maintenanceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completedAt":"2026-09-10T16:00:00Z",
                                  "actionsTaken":"Replaced worn component and completed safety test.",
                                  "actualCost":140.00,
                                  "currency":"USD",
                                  "equipmentOutcome":"%s",
                                  "maintenanceVersion":%d,
                                  "equipmentVersion":%d
                                }
                                """.formatted(outcome, maintenanceVersion, equipmentVersion)))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected long maintenanceVersion(UUID id) {
        return jdbcTemplate.queryForObject(
                "select version from gym.maintenances where id=?",
                Long.class, id);
    }

    protected long equipmentVersion() {
        return jdbcTemplate.queryForObject(
                "select version from gym.equipment where id=?",
                Long.class, equipmentId);
    }

    protected String equipmentStatus() {
        return jdbcTemplate.queryForObject(
                "select status from gym.equipment where id=?",
                String.class, equipmentId);
    }

    protected String maintenanceStatus(UUID id) {
        return jdbcTemplate.queryForObject(
                "select status from gym.maintenances where id=?",
                String.class, id);
    }
}
