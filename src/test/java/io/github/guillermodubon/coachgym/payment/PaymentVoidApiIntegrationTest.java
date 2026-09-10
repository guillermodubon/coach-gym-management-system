package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PaymentVoidApiIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void administratorVoidsPaidPaymentAndPreservesOriginalFinancialData()
            throws Exception {

        MockHttpSession admin = loginAsAdmin();

        PaymentFixture fixture = createPaidPayment(
                admin,
                "BANK_TRANSFER",
                "ORIGINAL-TRANSFER-001");

        Map<String, Object> before =
                paymentRow(fixture.paymentId());

        mockMvc.perform(
                        voidPayment(
                                admin,
                                fixture.paymentId(),
                                "Payment registered twice",
                                fixture.version()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.paymentCode")
                        .value(fixture.paymentCode()))
                .andExpect(jsonPath("$.correctionType")
                        .value("VOID"))
                .andExpect(jsonPath("$.previousStatus")
                        .value("PAID"))
                .andExpect(jsonPath("$.currentStatus")
                        .value("VOIDED"))
                .andExpect(jsonPath("$.reason")
                        .value("Payment registered twice"))
                .andExpect(jsonPath("$.correctedAt")
                        .isNotEmpty())
                .andExpect(jsonPath("$.correctedByUserId")
                        .isNotEmpty())
                .andExpect(jsonPath("$.version")
                        .value(1))
                .andExpect(jsonPath("$.refund")
                        .doesNotExist());

        Map<String, Object> after =
                paymentRow(fixture.paymentId());

        assertThat(after.get("status"))
                .isEqualTo("VOIDED");

        assertThat(((Number) after.get("version")).longValue())
                .isEqualTo(1L);

        assertOriginalFieldsUnchanged(
                before,
                after);

        assertThat(correctionHistoryCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(refundCount(fixture.paymentId()))
                .isZero();

        assertThat(correctionAuditCount(fixture.paymentId()))
                .isEqualTo(1);

        Map<String, Object> history =
                jdbcTemplate.queryForMap(
                        """
                        select
                            previous_status,
                            new_status,
                            reason,
                            occurred_at,
                            changed_by_user_id
                        from gym.payment_status_history
                        where payment_id = ?
                          and previous_status is not null
                        """,
                        fixture.paymentId());

        assertThat(history.get("previous_status"))
                .isEqualTo("PAID");

        assertThat(history.get("new_status"))
                .isEqualTo("VOIDED");

        assertThat(history.get("reason"))
                .isEqualTo("Payment registered twice");

        assertThat(history.get("occurred_at"))
                .isNotNull();

        assertThat(history.get("changed_by_user_id"))
                .isNotNull();

        mockMvc.perform(
                        get(
                                "/api/v1/payments/{paymentId}/correction",
                                fixture.paymentId())
                                .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.paymentCode")
                        .value(fixture.paymentCode()))
                .andExpect(jsonPath("$.correctionType")
                        .value("VOID"))
                .andExpect(jsonPath("$.previousStatus")
                        .value("PAID"))
                .andExpect(jsonPath("$.currentStatus")
                        .value("VOIDED"))
                .andExpect(jsonPath("$.reason")
                        .value("Payment registered twice"))
                .andExpect(jsonPath("$.version")
                        .value(1))
                .andExpect(jsonPath("$.refund")
                        .doesNotExist());
    }

    @Test
    void repeatedVoidAndRefundAfterVoidReturnConflict()
            throws Exception {

        MockHttpSession admin = loginAsAdmin();

        PaymentFixture fixture = createPaidPayment(
                admin,
                "CASH",
                null);

        mockMvc.perform(
                        voidPayment(
                                admin,
                                fixture.paymentId(),
                                "Duplicate payment",
                                0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus")
                        .value("VOIDED"))
                .andExpect(jsonPath("$.version")
                        .value(1));

        mockMvc.perform(
                        voidPayment(
                                admin,
                                fixture.paymentId(),
                                "Second void attempt",
                                1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_STATE_CONFLICT"));

        mockMvc.perform(
                        refundPayment(
                                admin,
                                fixture.paymentId(),
                                "Refund after void",
                                null,
                                1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_STATE_CONFLICT"));

        assertThat(paymentRow(fixture.paymentId()).get("status"))
                .isEqualTo("VOIDED");

        assertThat(refundCount(fixture.paymentId()))
                .isZero();

        assertThat(correctionHistoryCount(fixture.paymentId()))
                .isEqualTo(1);

        assertThat(correctionAuditCount(fixture.paymentId()))
                .isEqualTo(1);
    }

    private static void assertOriginalFieldsUnchanged(
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