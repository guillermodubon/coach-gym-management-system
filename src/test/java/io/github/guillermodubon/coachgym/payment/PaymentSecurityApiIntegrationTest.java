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
    void maintenanceUserCannotRegisterPayment() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        MockHttpSession maintenance = loginAsMaintenance();
        UUID clientId = createClient(admin, uniqueEmail());
        UUID planId = createPlan(admin, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(admin, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(maintenance)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isForbidden());

        assertThat(countPaymentRows(membershipId)).isZero();
    }

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
    void maintenanceUserCannotGetPayment() throws Exception {
        MockHttpSession maintenance = loginAsMaintenance();

        mockMvc.perform(
                        get("/api/v1/payments/{id}", UUID.randomUUID())
                                .session(maintenance))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotGetPayment() throws Exception {
        mockMvc.perform(
                        get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void maintenanceUserCannotListPayments() throws Exception {
        MockHttpSession maintenance = loginAsMaintenance();

        mockMvc.perform(
                        get("/api/v1/payments")
                                .session(maintenance))
                .andExpect(status().isForbidden());
    }
}
