package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class PaymentValidationApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {

    @Test
    void rejectsAmountThatDoesNotMatchFinalPrice() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "20.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(countPaymentRows(membershipId)).isZero();
    }

    @Test
    void rejectsCurrencyMismatch() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "EUR", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_CURRENCY_MISMATCH"));

        assertThat(countPaymentRows(membershipId)).isZero();
    }

    @Test
    void rejectsCancelledMembership() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        // Use a past start date so cancellation date can be within the period
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-08-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        // Cancel the membership first
        mockMvc.perform(
                        post("/api/v1/memberships/{id}/cancel", membershipId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cancelledOn": "2026-08-25",
                                          "reason": "Integration test cancellation",
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_MEMBERSHIP_STATE_CONFLICT"));

        assertThat(countPaymentRows(membershipId)).isZero();
    }

    @Test
    void rejectsFuturePaidAt() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2099-12-31T23:59:59Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_VALIDATION_FAILED"));

        assertThat(countPaymentRows(membershipId)).isZero();
    }

    @Test
    void rejectsDuplicateExternalReference() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);
        String ref = "DUPE-REF-" + UUID.randomUUID().toString().substring(0, 8);

        // First payment succeeds
        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CARD", ref,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated());

        // Create another membership + period for a second payment attempt
        UUID clientId2 = createClient(session, uniqueEmail());
        UUID membershipId2 = createMembership(session, clientId2, planId, null, "2026-09-01");
        UUID periodId2 = getMembershipPeriodId(membershipId2);

        // Second payment with same CARD + ref must be rejected
        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId2, membershipId2, periodId2,
                                        "25.00", "USD", "CARD", ref,
                                        "2026-08-25T14:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_PAYMENT_REFERENCE"));

        assertThat(countPaymentRows(membershipId2)).isZero();
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMembershipNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        unknownId, unknownId, unknownId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_MEMBERSHIP_NOT_FOUND"));
    }
}
