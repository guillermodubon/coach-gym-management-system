package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardSecurityApiIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void anonymousDashboardRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCurrentRolesCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk());
    }
}
