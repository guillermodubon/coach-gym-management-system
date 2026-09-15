package io.github.guillermodubon.coachgym.access;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AccessSecurityApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejectedWithoutSideEffects() throws Exception {
        mockMvc.perform(post("/api/v1/access/check-in").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"XYZ-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/access/records"))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isZero();
        org.assertj.core.api.Assertions.assertThat(countAccessAudits()).isZero();
    }


    @Test
    void postRequiresCsrfButGetDoesNot() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/access/check-in").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"XYZ-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/access/records").session(session))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isZero();
    }

    @Test
    void adminAndReceptionistCanPost() throws Exception {
        checkIn(loginAsAdmin(), "XYZ-ADMIN");
        checkIn(loginAsReceptionist(), "XYZ-RECEPTIONIST");
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isEqualTo(2);
    }
}
