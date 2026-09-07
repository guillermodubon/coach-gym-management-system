package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIncidentMetricIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void criticalReportedIncidentIsReflectedInCurrentSnapshot() throws Exception {
        reportIncident(loginAsAdmin(), "CRITICAL", true, 0L);

        Long criticalOpen = jdbcTemplate.queryForObject("""
                select count(*)
                from gym.incidents
                where priority = 'CRITICAL'
                  and status in ('OPEN', 'IN_PROGRESS')
                """, Long.class);

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents.criticalOpen")
                        .value(criticalOpen));

        assertThat(criticalOpen).isPositive();
    }
}
