package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardOperationalMetricsIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void administratorMetricsMatchCurrentDatabaseSnapshots() throws Exception {
        jdbcTemplate.update("""
                update gym.equipment
                set status = 'OUT_OF_SERVICE'
                where id = ?
                """, equipmentId);

        long available = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment where status = 'AVAILABLE'",
                Long.class);
        long maintenance = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment where status = 'MAINTENANCE'",
                Long.class);
        long outOfService = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment where status = 'OUT_OF_SERVICE'",
                Long.class);

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.available").value(available))
                .andExpect(jsonPath("$.equipment.inMaintenance").value(maintenance))
                .andExpect(jsonPath("$.equipment.outOfService").value(outOfService))
                .andExpect(jsonPath("$.memberships.active")
                        .value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.access.allowedToday")
                        .value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.payments.registeredAmount")
                        .value(greaterThanOrEqualTo(0.0)));
    }
}
