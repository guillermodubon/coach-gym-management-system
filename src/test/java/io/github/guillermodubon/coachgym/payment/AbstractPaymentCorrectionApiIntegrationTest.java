package io.github.guillermodubon.coachgym.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class AbstractPaymentCorrectionApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {

    protected PaymentFixture createPaidPayment(
            MockHttpSession adminSession,
            String method,
            String externalReference) throws Exception {
        UUID clientId = createClient(adminSession, uniqueEmail());
        UUID planId = createPlan(
                adminSession,
                uniqueName("Correction-Plan"),
                "25.00",
                "USD");
        UUID membershipId = createMembership(
                adminSession,
                clientId,
                planId,
                null,
                "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(adminSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId,
                                        membershipId,
                                        periodId,
                                        "25.00",
                                        "USD",
                                        method,
                                        externalReference,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = responseId(result);
        String paymentCode = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.paymentCode");

        return new PaymentFixture(
                paymentId,
                paymentCode,
                clientId,
                membershipId,
                periodId,
                new BigDecimal("25.00"),
                "USD",
                method,
                externalReference,
                0L);
    }

    protected MockHttpServletRequestBuilder voidPayment(
            MockHttpSession session,
            UUID paymentId,
            String reason,
            long version) throws Exception {
        return post("/api/v1/payments/{paymentId}/void", paymentId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "%s",
                                          "version": %d
                                        }
                                        """.formatted(reason, version));
    }

    protected MockHttpServletRequestBuilder refundPayment(
            MockHttpSession session,
            UUID paymentId,
            String reason,
            String externalReference,
            long version) throws Exception {
        String reference = externalReference == null
                ? "null"
                : "\"" + externalReference + "\"";
        return post("/api/v1/payments/{paymentId}/refund", paymentId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "%s",
                                          "externalReference": %s,
                                          "version": %d
                                        }
                                        """.formatted(reason, reference, version));
    }

    protected Map<String, Object> paymentRow(UUID paymentId) {
        return jdbcTemplate.queryForMap("""
                select payment_code, client_id, membership_id,
                       membership_period_id, amount, currency,
                       payment_method, status, external_reference,
                       paid_at, registered_by_user_id, created_at, version
                from gym.payments
                where id = ?
                """, paymentId);
    }

    protected int correctionHistoryCount(UUID paymentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from gym.payment_status_history
                where payment_id = ?
                  and previous_status is not null
                """, Integer.class, paymentId);
        return count == null ? 0 : count;
    }

    protected int refundCount(UUID paymentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_refunds where payment_id = ?",
                Integer.class,
                paymentId);
        return count == null ? 0 : count;
    }

    protected int correctionAuditCount(UUID paymentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from gym.audit_entries
                where resource_type = 'PAYMENT'
                  and resource_id = ?
                  and action_code in ('PAYMENT_VOIDED', 'PAYMENT_REFUNDED')
                """, Integer.class, paymentId);
        return count == null ? 0 : count;
    }

    protected record PaymentFixture(
            UUID paymentId,
            String paymentCode,
            UUID clientId,
            UUID membershipId,
            UUID periodId,
            BigDecimal amount,
            String currency,
            String method,
            String externalReference,
            long version) {
    }
}
