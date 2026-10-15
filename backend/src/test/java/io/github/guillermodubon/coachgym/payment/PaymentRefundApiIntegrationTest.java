package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PaymentRefundApiIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void administratorRecordsFullRefundUsingOriginalPaymentValues()
            throws Exception {

        MockHttpSession admin = loginAsAdmin();

        PaymentFixture fixture = createPaidPayment(
                admin,
                "CASH",
                null);

        Map<String, Object> before =
                paymentRow(fixture.paymentId());

        mockMvc.perform(
                        refundPayment(
                                admin,
                                fixture.paymentId(),
                                "Approved customer refund",
                                "REFUND-AUTH-001",
                                fixture.version()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.paymentCode")
                        .value(fixture.paymentCode()))
                .andExpect(jsonPath("$.correctionType")
                        .value("REFUND"))
                .andExpect(jsonPath("$.previousStatus")
                        .value("PAID"))
                .andExpect(jsonPath("$.currentStatus")
                        .value("REFUNDED"))
                .andExpect(jsonPath("$.reason")
                        .value("Approved customer refund"))
                .andExpect(jsonPath("$.version")
                        .value(1))
                .andExpect(jsonPath("$.refund.refundId")
                        .isNotEmpty())
                .andExpect(jsonPath("$.refund.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.refund.amount")
                        .value(25.00))
                .andExpect(jsonPath("$.refund.currency")
                        .value("USD"))
                .andExpect(jsonPath("$.refund.reason")
                        .value("Approved customer refund"))
                .andExpect(jsonPath("$.refund.externalReference")
                        .value("REFUND-AUTH-001"))
                .andExpect(jsonPath("$.refund.refundedAt")
                        .isNotEmpty())
                .andExpect(jsonPath("$.refund.refundedByUserId")
                        .isNotEmpty());

        Map<String, Object> after =
                paymentRow(fixture.paymentId());

        assertThat(after.get("status"))
                .isEqualTo("REFUNDED");

        assertThat(((Number) after.get("version")).longValue())
                .isEqualTo(1L);

        assertOriginalPaymentFieldsUnchanged(
                before,
                after);

        Map<String, Object> refund =
                jdbcTemplate.queryForMap(
                        """
                        select
                            amount,
                            currency,
                            refund_method,
                            reason,
                            external_reference,
                            refunded_at,
                            refunded_by_user_id
                        from gym.payment_refunds
                        where payment_id = ?
                        """,
                        fixture.paymentId());

        assertThat((BigDecimal) refund.get("amount"))
                .isEqualByComparingTo(fixture.amount());

        assertThat(refund.get("currency")
                .toString()
                .strip())
                .isEqualTo(fixture.currency());

        assertThat(refund.get("refund_method"))
                .isEqualTo(fixture.method());

        assertThat(refund.get("reason"))
                .isEqualTo("Approved customer refund");

        assertThat(refund.get("external_reference"))
                .isEqualTo("REFUND-AUTH-001");

        assertThat(refund.get("refunded_at"))
                .isNotNull();

        assertThat(refund.get("refunded_by_user_id"))
                .isNotNull();

        assertThat(refundCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(correctionHistoryCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(correctionAuditCount(fixture.paymentId()))
                .isEqualTo(1);

        mockMvc.perform(
                        get(
                                "/api/v1/payments/{paymentId}/correction",
                                fixture.paymentId())
                                .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.correctionType")
                        .value("REFUND"))
                .andExpect(jsonPath("$.currentStatus")
                        .value("REFUNDED"))
                .andExpect(jsonPath("$.refund.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.refund.amount")
                        .value(25.00))
                .andExpect(jsonPath("$.refund.currency")
                        .value("USD"))
                .andExpect(jsonPath("$.refund.externalReference")
                        .value("REFUND-AUTH-001"));
    }

    @Test
    void repeatedRefundAndVoidAfterRefundReturnConflict()
            throws Exception {

        MockHttpSession admin = loginAsAdmin();

        PaymentFixture fixture = createPaidPayment(
                admin,
                "CASH",
                null);

        mockMvc.perform(
                        refundPayment(
                                admin,
                                fixture.paymentId(),
                                "Approved refund",
                                null,
                                0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus")
                        .value("REFUNDED"))
                .andExpect(jsonPath("$.version")
                        .value(1));

        mockMvc.perform(
                        refundPayment(
                                admin,
                                fixture.paymentId(),
                                "Second refund",
                                null,
                                1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_STATE_CONFLICT"));

        mockMvc.perform(
                        voidPayment(
                                admin,
                                fixture.paymentId(),
                                "Void after refund",
                                1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_STATE_CONFLICT"));

        assertThat(paymentRow(fixture.paymentId()).get("status"))
                .isEqualTo("REFUNDED");

        assertThat(refundCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(correctionHistoryCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(correctionAuditCount(fixture.paymentId()))
                .isEqualTo(1);
    }

    private static void assertOriginalPaymentFieldsUnchanged(
            Map<String, Object> before,
            Map<String, Object> after) {

        for (String field : new String[] {
                "payment_code",
                "client_id",
                "membership_id",
                "membership_period_id",
                "amount",
                "currency",
                "payment_method",
                "external_reference",
                "paid_at",
                "registered_by_user_id",
                "created_at"
        }) {
            assertThat(after.get(field))
                    .as("Original payment field %s", field)
                    .isEqualTo(before.get(field));
        }
    }
}