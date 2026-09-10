package io.github.guillermodubon.coachgym.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionHistoryApiIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void historyExcludesInitialRegistrationAndIsPaginated() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/status-history",
                        fixture.paymentId())
                        .session(admin)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.empty").value(true));

        mockMvc.perform(voidPayment(
                        admin,
                        fixture.paymentId(),
                        "Registered twice",
                        0))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/status-history",
                        fixture.paymentId())
                        .session(admin)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].previousStatus")
                        .value("PAID"))
                .andExpect(jsonPath("$.content[0].newStatus")
                        .value("VOIDED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidHistoryPaginationReturnsBadRequest() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/status-history",
                        fixture.paymentId())
                        .session(admin)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_CORRECTION_VALIDATION_FAILED"));
    }
}
