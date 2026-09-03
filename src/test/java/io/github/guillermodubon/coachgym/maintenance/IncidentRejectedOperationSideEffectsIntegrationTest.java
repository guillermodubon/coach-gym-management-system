package io.github.guillermodubon.coachgym.maintenance;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ensures rejected operations do not mutate state, history, equipment, or audit. */
class IncidentRejectedOperationSideEffectsIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void receptionistLifecycleDenialHasNoSideEffects() throws Exception {
        UUID incidentId = reportIncident(loginAsAdmin(), "HIGH", false, null);
        Snapshot before = snapshot(incidentId);

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(loginAsReceptionist())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Unauthorized attempt.","version":0}
                                """))
                .andExpect(status().isForbidden());

        assertThat(snapshot(incidentId)).isEqualTo(before);
    }

    @Test
    void missingCsrfHasNoSideEffects() throws Exception {
        UUID incidentId = reportIncident(loginAsAdmin(), "HIGH", false, null);
        Snapshot before = snapshot(incidentId);

        mockMvc.perform(post("/api/v1/incidents/{id}/priority", incidentId)
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority":"CRITICAL",
                                  "reason":"No CSRF.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(snapshot(incidentId)).isEqualTo(before);
    }

    @Test
    void staleIncidentVersionHasNoSideEffects() throws Exception {
        UUID incidentId = reportIncident(loginAsAdmin(), "HIGH", false, null);
        Snapshot before = snapshot(incidentId);

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Stale version.","version":99}
                                """))
                .andExpect(status().isConflict());

        assertThat(snapshot(incidentId)).isEqualTo(before);
    }

    private Snapshot snapshot(UUID incidentId) {
        Map<String, Object> incident = jdbcTemplate.queryForMap("""
                select status, priority, version, resolved_at,
                       resolved_by_user_id, resolution_notes
                from gym.incidents
                where id=?
                """, incidentId);

        int incidentHistoryCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history where incident_id=?",
                Integer.class,
                incidentId);

        Map<String, Object> equipment = jdbcTemplate.queryForMap("""
                select status, version
                from gym.equipment
                where id=?
                """, equipmentId);

        int equipmentHistoryCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class,
                equipmentId);

        int incidentAuditCount = jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type='INCIDENT' and resource_id=?
                """, Integer.class, incidentId);

        return new Snapshot(
                String.valueOf(incident.get("status")),
                String.valueOf(incident.get("priority")),
                ((Number) incident.get("version")).longValue(),
                incident.get("resolved_at"),
                incident.get("resolved_by_user_id"),
                incident.get("resolution_notes"),
                incidentHistoryCount,
                String.valueOf(equipment.get("status")),
                ((Number) equipment.get("version")).longValue(),
                equipmentHistoryCount,
                incidentAuditCount);
    }

    private record Snapshot(
            String incidentStatus,
            String incidentPriority,
            long incidentVersion,
            Object resolvedAt,
            Object resolvedByUserId,
            Object resolutionNotes,
            int incidentHistoryCount,
            String equipmentStatus,
            long equipmentVersion,
            int equipmentHistoryCount,
            int incidentAuditCount) {
    }
}
