package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientOperationalProfileApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void currentRolesCanReadOperationalProfileWithoutInventedRelations()
            throws Exception {
        var clientId = insertProfileClient(
                "BlockEight",
                "Profile",
                "block.eight.profile@example.com",
                "+50370008010");

        mockMvc.perform(get("/api/v1/clients/{id}/profile", clientId)
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()))
                .andExpect(jsonPath("$.clientCode").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("BlockEight"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentMembership")
                        .value((Object) null))
                .andExpect(jsonPath("$.currentPeriodPayment")
                        .value((Object) null))
                .andExpect(jsonPath("$.lastAccess")
                        .value((Object) null))
                .andExpect(jsonPath("$.photo")
                        .value((Object) null));

        mockMvc.perform(get("/api/v1/clients/{id}/profile", clientId)
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()));
    }

    @Test
    void missingProfileReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/profile",
                        java.util.UUID.randomUUID())
                        .session(loginAsAdmin()))
                .andExpect(status().isNotFound());
    }
}
