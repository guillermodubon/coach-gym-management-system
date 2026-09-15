package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientUpdateApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void adminAndReceptionistCanUpdateAllowedProfileFields() throws Exception {
        var adminClient = insertProfileClient(
                "Admin",
                "Before",
                "admin.before@example.com",
                "+50370008011");

        mockMvc.perform(put("/api/v1/clients/{id}", adminClient)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(
                                "updated.admin@example.com",
                                clientVersion(adminClient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Profile"))
                .andExpect(jsonPath("$.emergencyContact.fullName")
                        .value("Emergency Contact"));

        var receptionistClient = insertProfileClient(
                "Reception",
                "Before",
                "reception.before@example.com",
                "+50370008012");

        mockMvc.perform(put("/api/v1/clients/{id}", receptionistClient)
                        .session(loginAsReceptionist())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(
                                "updated.receptionist@example.com",
                                clientVersion(receptionistClient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+50370009999"));
    }

    @Test
    void updateRequiresCsrfAndCurrentVersion() throws Exception {
        var clientId = insertProfileClient(
                "Security",
                "Update",
                "security.update@example.com",
                "+50370008013");

        mockMvc.perform(put("/api/v1/clients/{id}", clientId)
                        .session(loginAsReceptionist())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(
                                "csrf.update@example.com",
                                clientVersion(clientId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/clients/{id}", clientId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(
                                "version.conflict@example.com",
                                99L)))
                .andExpect(status().isConflict());
    }
}
