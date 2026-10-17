package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentSecurityApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {


    @Test
    void unauthenticatedUserCannotRegisterPayment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "clientId": "%s",
                                          "membershipId": "%s",
                                          "membershipPeriodId": "%s",
                                          "amount": 25.00,
                                          "currency": "USD",
                                          "paymentMethod": "CASH",
                                          "paidAt": "2026-08-25T12:00:00Z"
                                        }
                                        """.formatted(UUID.randomUUID(),
                                        UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(
                        post("/api/v1/payments")
                                // no .with(csrf())
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "clientId": "%s" }
                                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }


    @Test
    void unauthenticatedUserCannotGetPayment() throws Exception {
        mockMvc.perform(
                        get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

}
