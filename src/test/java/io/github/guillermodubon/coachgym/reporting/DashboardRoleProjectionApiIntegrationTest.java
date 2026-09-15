package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardRoleProjectionApiIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void administratorReceivesEveryDashboardSection() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.period.from").exists())
                .andExpect(jsonPath("$.period.until").exists())
                .andExpect(jsonPath("$.memberships").isMap())
                .andExpect(jsonPath("$.access").isMap())
                .andExpect(jsonPath("$.payments").isMap())
                .andExpect(jsonPath("$.equipment").isMap())
                .andExpect(jsonPath("$.incidents").isMap())
                .andExpect(jsonPath("$.maintenance").isMap())
                .andExpect(jsonPath("$.notifications").isMap());
    }

    @Test
    void receptionistReceivesOnlyAuthorizedSections() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships").isMap())
                .andExpect(jsonPath("$.access").isMap())
                .andExpect(jsonPath("$.notifications").isMap())
                .andExpect(jsonPath("$.payments").value((Object) null))
                .andExpect(jsonPath("$.equipment").value((Object) null))
                .andExpect(jsonPath("$.incidents").value((Object) null))
                .andExpect(jsonPath("$.maintenance").value((Object) null));
    }
}
