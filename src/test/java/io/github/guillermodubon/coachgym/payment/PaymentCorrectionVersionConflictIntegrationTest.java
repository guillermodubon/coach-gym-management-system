package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PaymentCorrectionVersionConflictIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    @Test
    void staleVersionIsRejectedWithoutPartialWrites() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(voidPayment(
                        admin,
                        fixture.paymentId(),
                        "Stale version attempt",
                        99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERSION_CONFLICT"));

        assertThat(paymentRow(fixture.paymentId()).get("status"))
                .isEqualTo("PAID");
        assertThat(refundCount(fixture.paymentId())).isZero();
        assertThat(correctionHistoryCount(fixture.paymentId())).isZero();
        assertThat(correctionAuditCount(fixture.paymentId())).isZero();
    }

    @Test
    void sameOriginalVersionCannotApplyTwoDifferentCorrections()
            throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(voidPayment(
                        admin,
                        fixture.paymentId(),
                        "First correction wins",
                        0))
                .andExpect(status().isOk());

        mockMvc.perform(refundPayment(
                        admin,
                        fixture.paymentId(),
                        "Concurrent stale refund",
                        null,
                        0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERSION_CONFLICT"));

        assertThat(paymentRow(fixture.paymentId()).get("status"))
                .isEqualTo("VOIDED");
        assertThat(refundCount(fixture.paymentId())).isZero();
        assertThat(correctionHistoryCount(fixture.paymentId())).isEqualTo(1);
    }
}
