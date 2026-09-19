package io.github.guillermodubon.coachgym.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ActuatorHealthIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void exposesSafeLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("components"))));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("components"))));
    }

    @Test
    void doesNotExposeDiagnosticActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
