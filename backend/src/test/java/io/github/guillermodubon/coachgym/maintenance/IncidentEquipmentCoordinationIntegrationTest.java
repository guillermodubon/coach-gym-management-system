package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentEquipmentCoordinationIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void receptionistReportCanAtomicallyTakeAvailableEquipmentOutOfService()
            throws Exception {
        reportIncident(loginAsReceptionist(), "CRITICAL", true, 0L);

        String equipmentStatus = jdbcTemplate.queryForObject(
                "select status from gym.equipment where id=?",
                String.class, equipmentId);
        Integer incidentCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incidents where equipment_id=?",
                Integer.class, equipmentId);
        Integer equipmentHistory = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class, equipmentId);

        org.assertj.core.api.Assertions.assertThat(equipmentStatus)
                .isEqualTo("OUT_OF_SERVICE");
        org.assertj.core.api.Assertions.assertThat(incidentCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(equipmentHistory).isEqualTo(1);
    }

    @Test
    void reportWithoutWithdrawalKeepsEquipmentAvailable() throws Exception {
        reportIncident(loginAsAdmin(), "LOW", false, null);
        String equipmentStatus = jdbcTemplate.queryForObject(
                "select status from gym.equipment where id=?",
                String.class, equipmentId);
        org.assertj.core.api.Assertions.assertThat(equipmentStatus)
                .isEqualTo("AVAILABLE");
    }

    @Test
    void staleEquipmentVersionRollsBackIncidentAndHistory() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "priority":"CRITICAL",
                                  "description":"Safety failure.",
                                  "takeOutOfService":true,
                                  "equipmentVersion":99
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_VERSION_CONFLICT"));

        Integer incidentCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incidents", Integer.class);
        Integer incidentHistory = jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history", Integer.class);
        String equipmentStatus = jdbcTemplate.queryForObject(
                "select status from gym.equipment where id=?",
                String.class, equipmentId);

        org.assertj.core.api.Assertions.assertThat(incidentCount).isZero();
        org.assertj.core.api.Assertions.assertThat(incidentHistory).isZero();
        org.assertj.core.api.Assertions.assertThat(equipmentStatus)
                .isEqualTo("AVAILABLE");
    }

    @Test
    void retiredEquipmentRejectsReport() throws Exception {
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
                                  "description":"Late report.",
                                  "takeOutOfService":false
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_EQUIPMENT_RETIRED"));
    }
}
