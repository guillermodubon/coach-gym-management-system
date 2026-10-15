package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionSecurityApiIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void anonymousUserCannotMutateOrReadCorrections() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/void",
                        fixture.paymentId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Registered twice","version":0}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/status-history",
                        fixture.paymentId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCannotVoidOrRefundButCanReadHistoryAndCorrection()
            throws Exception {
        MockHttpSession admin = loginAsAdmin();
        MockHttpSession receptionist = loginAsReceptionist();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(voidPayment(
                        receptionist,
                        fixture.paymentId(),
                        "Receptionist void attempt",
                        0))
                .andExpect(status().isForbidden());

        mockMvc.perform(refundPayment(
                        receptionist,
                        fixture.paymentId(),
                        "Receptionist refund attempt",
                        null,
                        0))
                .andExpect(status().isForbidden());

        mockMvc.perform(voidPayment(
                        admin,
                        fixture.paymentId(),
                        "Administrator correction",
                        0))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/correction",
                        fixture.paymentId()).session(receptionist))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/payments/{paymentId}/status-history",
                        fixture.paymentId()).session(receptionist))
                .andExpect(status().isOk());
    }
}
