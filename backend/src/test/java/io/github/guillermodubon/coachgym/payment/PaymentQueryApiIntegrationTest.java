package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PaymentQueryApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {

    @Test
    void getByIdReturnsRegisteredPayment() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Plan"), "25.00", "USD");
        UUID membershipId = createMembership(session, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        MvcResult created = mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = responseId(created);

        mockMvc.perform(
                        get("/api/v1/payments/{id}", paymentId)
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.paymentCode")
                        .value(Matchers.matchesPattern("PAY-[0-9]{6}")))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }

    @Test
    void getByIdReturnsNotFoundForUnknownPayment() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(
                        get("/api/v1/payments/{id}", UUID.randomUUID())
                                .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void listPaymentsReturnsPaginatedResults() throws Exception {
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
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/payments")
                                .param("clientId", clientId.toString())
                                .param("page", "0")
                                .param("size", "10")
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void listPaymentsWithInvalidPageSizeReturns400() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(
                        get("/api/v1/payments")
                                .param("size", "0")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_VALIDATION_FAILED"));
    }

    @Test
    void listPaymentsWithInvalidSortFieldReturns400() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(
                        get("/api/v1/payments")
                                .param("sort", "INVALID_FIELD")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_VALIDATION_FAILED"));
    }
}
