package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Final OpenAPI contract for client search, profile, lifecycle, and photo access. */
class ClientProfileOpenApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void documentsClientSearchAndOperationalProfileReads() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/status-history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/photo'].get").exists());
    }

    @Test
    void documentsClientProfileAndLifecycleMutations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/deactivate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/reactivate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/photo'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/photo'].delete").exists());
    }

    @Test
    void documentsSearchFiltersPaginationAndSorting() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "search",
                                "status",
                                "membershipStatus",
                                "branchId",
                                "page",
                                "size",
                                "sort",
                                "direction")))
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get.parameters[?(@.name == 'page')].schema.default")
                        .value(org.hamcrest.Matchers.hasItem(0)))
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get.parameters[?(@.name == 'size')].schema.default")
                        .value(org.hamcrest.Matchers.hasItem(25)));
    }

    @Test
    void documentsSessionSecurityForEveryClientProfileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}'].put.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/deactivate'].post.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/photo'].put.security[0]")
                        .value(hasKey("sessionCookie")));
    }

    @Test
    void documentsCurrentRolesAndDoesNotRestoreMaintenanceRole()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("ADMIN")))
                .andExpect(content().string(
                        containsString("RECEPTIONIST")))
                .andExpect(content().string(
                        not(containsString(
                                "ROLE_MAINTENANCE"))))
                .andExpect(content().string(
                        not(containsString(
                                "hasRole('MAINTENANCE')"))));
    }

    @Test
    void doesNotDocumentUnsupportedClientDeletion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}'].delete")
                        .doesNotExist());
    }
}
