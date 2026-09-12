package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentAttemptSecurityApiIntegrationTest extends AbstractPaymentApiIntegrationTest {

    @Test
    void staffCreateRequiresSessionAndCsrf() throws Exception {
        String body = """
                {
                  "clientId": "%s",
                  "membershipId": "%s",
                  "membershipPeriodId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheExactPostWebhookRouteBypassesCsrfAndAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/v1/payment-provider/stripe/webhook"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/payment-provider/stripe/other")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
