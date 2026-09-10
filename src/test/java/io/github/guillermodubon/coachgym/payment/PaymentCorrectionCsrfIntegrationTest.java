package io.github.guillermodubon.coachgym.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionCsrfIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void voidAndRefundRequireCsrf() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture voidFixture = createPaidPayment(admin, "CASH", null);
        PaymentFixture refundFixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/void",
                        voidFixture.paymentId())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Registered twice","version":0}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/refund",
                        refundFixture.paymentId())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason":"Approved refund",
                                  "externalReference":null,
                                  "version":0
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
