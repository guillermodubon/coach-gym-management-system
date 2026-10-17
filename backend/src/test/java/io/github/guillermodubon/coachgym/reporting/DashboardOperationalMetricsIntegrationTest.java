package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardOperationalMetricsIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void accessMetricsCountPersistedAllowedAndDeniedDecisions() throws Exception {
        jdbcTemplate.execute("truncate table gym.access_records");
        jdbcTemplate.update("""
                insert into gym.access_records
                    (id, entered_code, decision, reason_code, details,
                     occurred_at, recorded_by_user_id)
                values (?, 'REPORTING-ALLOWED', 'ALLOWED', 'ACCESS_ALLOWED',
                        'Reporting integration fixture', current_timestamp, ?),
                       (?, 'REPORTING-DENIED', 'DENIED', 'IDENTIFIER_NOT_FOUND',
                        'Reporting integration fixture', current_timestamp, ?)
                """,
                java.util.UUID.randomUUID(), adminId,
                java.util.UUID.randomUUID(), adminId);

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.allowedToday").value(1))
                .andExpect(jsonPath("$.access.deniedToday").value(1));
    }

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
