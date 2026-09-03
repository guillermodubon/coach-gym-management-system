package io.github.guillermodubon.coachgym.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentSecurityApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/incidents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "priority":"HIGH",
                                  "description":"Failure.",
                                  "takeOutOfService":false
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isForbidden());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.incidents", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    @Test
    void receptionistCannotManageLifecycle() throws Exception {
        var admin = loginAsAdmin();
        var incidentId = reportIncident(admin, "HIGH", false, null);
        var receptionist = loginAsReceptionist();

        mockMvc.perform(post("/api/v1/incidents/{id}/start", incidentId)
                        .session(receptionist)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Not authorized.","version":0}
                                """))
                .andExpect(status().isForbidden());

        String status = jdbcTemplate.queryForObject(
                "select status from gym.incidents where id=?",
                String.class, incidentId);
        Long version = jdbcTemplate.queryForObject(
                "select version from gym.incidents where id=?",
                Long.class, incidentId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("OPEN");
        org.assertj.core.api.Assertions.assertThat(version).isZero();
    }
}
