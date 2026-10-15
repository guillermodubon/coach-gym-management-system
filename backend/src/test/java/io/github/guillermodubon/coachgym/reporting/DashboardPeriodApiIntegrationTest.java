package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardPeriodApiIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void returnsExplicitInclusivePeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin())
                        .param("from", "2026-01-01")
                        .param("until", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-01-01"))
                .andExpect(jsonPath("$.period.until").value("2026-01-31"));
    }

    @Test
    void rejectsInvertedPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin())
                        .param("from", "2026-02-01")
                        .param("until", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("REPORTING_VALIDATION_FAILED"));
    }

    @Test
    void rejectsPeriodLongerThanConfiguredMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin())
                        .param("from", "2024-01-01")
                        .param("until", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("REPORTING_VALIDATION_FAILED"));
    }

    @Test
    void rejectsMalformedIsoDate() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin())
                        .param("from", "01-01-2026"))
                .andExpect(status().isBadRequest());
    }
}
