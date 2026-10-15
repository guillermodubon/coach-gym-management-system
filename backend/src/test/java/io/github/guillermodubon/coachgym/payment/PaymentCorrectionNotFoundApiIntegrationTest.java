package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionNotFoundApiIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void unknownPaymentAndMissingCorrectionReturnNotFound() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/{paymentId}/void", unknown)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Unknown payment","version":0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        PaymentFixture paid = createPaidPayment(admin, "CASH", null);
        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/correction",
                        paid.paymentId()).session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }
}
