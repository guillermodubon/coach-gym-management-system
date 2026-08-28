package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PaymentRegistrationApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {

    @Test
    void adminRegistersPaymentSuccessfully() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(paymentBody(clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern("http://localhost/api/v1/payments/[0-9a-f-]+")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.paymentCode").value(matchesPattern("PAY-[0-9]{6}")))
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.membershipId").value(membershipId.toString()))
                .andExpect(jsonPath("$.membershipPeriodId").value(periodId.toString()))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.externalReference").doesNotExist())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        UUID paymentId = responseId(result);

        // DB row exists
        assertThat(countPaymentRows(membershipId)).isEqualTo(1);
        // Initial history row exists (null → PAID)
        assertThat(countPaymentHistoryRows(membershipId)).isEqualTo(1);
        // Audit row exists
        assertThat(countPaymentAuditRows(membershipId)).isEqualTo(1);

        // GET returns same payment
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/payments/{id}", paymentId)
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void receptionistRegistersPaymentSuccessfully() throws Exception {
        MockHttpSession session = loginAsReceptionist();
        MockHttpSession adminSession = loginAsAdmin();
        UUID clientId = createClient(adminSession, uniqueEmail());
        UUID planId = createPlan(adminSession, uniqueName("Plan"), "30.00", "USD");
        UUID membershipId = createMembership(adminSession, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(paymentBody(clientId, membershipId, periodId,
                                        "30.00", "USD", "CARD", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));
    }

    @Test
    void promotionFinalPriceIsUsedNotListPrice() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "100.00", "USD");

        // 10% promotion → finalPrice = 90.00
        UUID promoId = createPercentagePromotion(session, uniqueName("Promo"));
        long promoVersion = jdbcTemplate.queryForObject(
                "select version from gym.promotions where id=?",
                Long.class, promoId);
        addEligiblePlan(session, promoId, promoVersion, planId);

        UUID membershipId = createMembership(session, clientId, planId, promoId, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        // Must use 90.00 (finalPrice), not 100.00 (listPrice)
        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(paymentBody(clientId, membershipId, periodId,
                                        "90.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(90.00));
    }

    @Test
    void paymentWithExternalReference() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);
        String ref = "TXN-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(paymentBody(clientId, membershipId, periodId,
                                        "25.00", "USD", "BANK_TRANSFER", ref,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalReference").value(ref));
    }

    protected static String paymentBody(
            UUID clientId, UUID membershipId, UUID periodId,
            String amount, String currency, String method,
            String externalReference, String paidAt) {

        String refValue = externalReference == null ? "null"
                : "\"" + externalReference + "\"";

        return """
                {
                  "clientId": "%s",
                  "membershipId": "%s",
                  "membershipPeriodId": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "paymentMethod": "%s",
                  "externalReference": %s,
                  "paidAt": "%s"
                }
                """.formatted(clientId, membershipId, periodId,
                amount, currency, method, refValue, paidAt);
    }
}
