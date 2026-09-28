package io.github.guillermodubon.coachgym.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;

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
    void doesNotExposeDiagnosticActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
