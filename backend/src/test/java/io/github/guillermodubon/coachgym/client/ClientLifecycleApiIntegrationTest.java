package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientLifecycleApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void administratorCanDeactivateAndReactivateWithAppendOnlyHistory()
            throws Exception {
        var clientId = insertProfileClient(
                "Lifecycle",
                "Admin",
                "lifecycle.admin@example.com",
                "+50370008014");

        mockMvc.perform(post("/api/v1/clients/{id}/deactivate", clientId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecyclePayload(
                                "Administrative hold",
                                clientVersion(clientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(clientStatus(clientId)).isEqualTo("INACTIVE");

        mockMvc.perform(post("/api/v1/clients/{id}/reactivate", clientId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecyclePayload(
                                "Administrative review completed",
                                clientVersion(clientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/clients/{id}/status-history", clientId)
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId")
                        .value(clientId.toString()))
                .andExpect(jsonPath("$[1].clientId")
                        .value(clientId.toString()));

        Integer historyCount = jdbcTemplate.queryForObject("""
                select count(*)
                from gym.client_status_history
                where client_id = ?
                """, Integer.class, clientId);
        assertThat(historyCount).isEqualTo(2);
    }

    @Test
    void receptionistCannotChangeClientLifecycle() throws Exception {
        var clientId = insertProfileClient(
                "Lifecycle",
                "Reception",
                "lifecycle.reception@example.com",
                "+50370008015");

        mockMvc.perform(post("/api/v1/clients/{id}/deactivate", clientId)
                        .session(loginAsReceptionist())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecyclePayload(
                                "Not authorized",
                                clientVersion(clientId))))
                .andExpect(status().isForbidden());

        assertThat(clientStatus(clientId)).isEqualTo("ACTIVE");
    }

    @Test
    void lifecycleMutationsRequireCsrf() throws Exception {
        var clientId = insertProfileClient(
                "Lifecycle",
                "Csrf",
                "lifecycle.csrf@example.com",
                "+50370008016");

        mockMvc.perform(post("/api/v1/clients/{id}/deactivate", clientId)
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecyclePayload(
                                "Missing CSRF",
                                clientVersion(clientId))))
                .andExpect(status().isForbidden());
    }
}
