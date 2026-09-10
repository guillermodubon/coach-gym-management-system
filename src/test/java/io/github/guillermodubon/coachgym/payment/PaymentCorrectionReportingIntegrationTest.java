package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionReportingIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void onlyPaidPaymentsContributeToEffectiveRevenue() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        PaymentFixture effective = createPaidPayment(admin, "CASH", null);
        PaymentFixture voided = createPaidPayment(admin, "CASH", null);
        PaymentFixture refunded = createPaidPayment(admin, "CASH", null);

        assertThat(effectiveRevenue(effective, voided, refunded))
                .isEqualByComparingTo("75.00");

        mockMvc.perform(voidPayment(
                        admin,
                        voided.paymentId(),
                        "Duplicate payment",
                        0))
                .andExpect(status().isOk());

        mockMvc.perform(refundPayment(
                        admin,
                        refunded.paymentId(),
                        "Approved full refund",
                        null,
                        0))
                .andExpect(status().isOk());

        assertThat(effectiveRevenue(effective, voided, refunded))
                .isEqualByComparingTo("25.00");

        assertThat(paymentRow(effective.paymentId()).get("status"))
                .isEqualTo("PAID");
        assertThat(paymentRow(voided.paymentId()).get("status"))
                .isEqualTo("VOIDED");
        assertThat(paymentRow(refunded.paymentId()).get("status"))
                .isEqualTo("REFUNDED");
    }

    private BigDecimal effectiveRevenue(PaymentFixture... payments) {
        String placeholders = String.join(", ",
                java.util.Collections.nCopies(payments.length, "?"));
        Object[] ids = java.util.Arrays.stream(payments)
                .map(PaymentFixture::paymentId)
                .toArray();

        BigDecimal result = jdbcTemplate.queryForObject(
                "select coalesce(sum(amount), 0) from gym.payments "
                        + "where status = 'PAID' and id in ("
                        + placeholders + ")",
                BigDecimal.class,
                ids);
        return result == null ? BigDecimal.ZERO : result;
    }
}
