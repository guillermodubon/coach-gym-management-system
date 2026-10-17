package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    void concurrentCorrectionsAtTheSameVersionHaveOneWinnerAndNoPartialRefund()
            throws Exception {
        MockHttpSession voidSession = loginAsAdmin();
        MockHttpSession refundSession = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(voidSession, "CASH", null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> voidResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Payment correction race did not start.");
                }
                return mockMvc.perform(voidPayment(
                                voidSession, fixture.paymentId(), "Concurrent void", 0))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> refundResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Payment correction race did not start.");
                }
                return mockMvc.perform(refundPayment(
                                refundSession, fixture.paymentId(), "Concurrent refund", null, 0))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    voidResult.get(30, TimeUnit.SECONDS),
                    refundResult.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        var payment = paymentRow(fixture.paymentId());
        assertThat(payment.get("version")).isEqualTo(1L);
        assertThat(payment.get("status")).isIn("VOIDED", "REFUNDED");
        assertThat(correctionHistoryCount(fixture.paymentId())).isEqualTo(1);
        assertThat(correctionAuditCount(fixture.paymentId())).isEqualTo(1);
        if ("REFUNDED".equals(payment.get("status"))) {
            assertThat(refundCount(fixture.paymentId())).isEqualTo(1);
        } else {
            assertThat(refundCount(fixture.paymentId())).isZero();
        }
    }
}
