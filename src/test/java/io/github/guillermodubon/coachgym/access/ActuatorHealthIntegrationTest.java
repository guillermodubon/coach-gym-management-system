package io.github.guillermodubon.coachgym.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;

import java.util.List;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.Test;

class ActuatorHealthIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void exposesSafeLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("components"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("configuration"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("provider"))));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("components"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("configuration"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("provider"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("refresh-token"))));
    }

    @Test
    void readinessDetailsAreVisibleOnlyToAuthenticatedAdministratorsAndProbesDoNotMutate()
            throws Exception {
        List<Integer> before = protectedOperationalTableCounts();

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("components"))));

        mockMvc.perform(get("/actuator/health/readiness").session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("components")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("diskSpace")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("refresh-token"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(ADMIN_PASSWORD))));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(protectedOperationalTableCounts())
                .isEqualTo(before);
    }

    @Test
    void metricsRequireActivePersistedOrganizationAdministratorScope() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics").session(loginAsReceptionist()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").session(loginAsAdminWithActiveBranch()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "coachgym.authentication.login.attempts")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http.server.requests")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm.memory.used")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("process.uptime")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hikaricp.connections.active")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(ADMIN_USERNAME))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(ADMIN_PASSWORD))));

        java.util.UUID adminId = userId(ADMIN_USERNAME);
        jdbcTemplate.update(
                "update gym.staff_scopes set scope_type = 'BRANCH', version = version + 1 where user_id = ?",
                adminId);
        try {
            mockMvc.perform(get("/actuator/metrics").session(loginAsAdmin()))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update(
                    "update gym.staff_scopes set scope_type = 'ORGANIZATION', version = version + 1 where user_id = ?",
                    adminId);
        }
    }

    @Test
    void staleSessionsCannotReadMetricsAndDiagnosticEndpointsRemainDenied() throws Exception {
        MockHttpSession staleSession = loginAsAdmin();
        jdbcTemplate.update(
                "update gym.users set security_version = security_version + 1 where id = ?",
                userId(ADMIN_USERNAME));
        mockMvc.perform(get("/actuator/metrics").session(staleSession))
                .andExpect(status().isUnauthorized());

        MockHttpSession activeAdmin = loginAsAdmin();
        for (String endpoint : List.of(
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/mappings",
                "/actuator/loggers",
                "/actuator/heapdump")) {
            mockMvc.perform(get(endpoint).session(activeAdmin))
                    .andExpect(status().isForbidden());
        }
    }

    private List<Integer> protectedOperationalTableCounts() {
        return List.of(
                jdbcTemplate.queryForObject("select count(*) from gym.email_deliveries", Integer.class),
                jdbcTemplate.queryForObject(
                        "select count(*) from gym.email_delivery_attempts", Integer.class),
                jdbcTemplate.queryForObject(
                        "select count(*) from gym.staff_account_activation_deliveries", Integer.class),
                jdbcTemplate.queryForObject("select count(*) from gym.audit_entries", Integer.class));
    }
}
