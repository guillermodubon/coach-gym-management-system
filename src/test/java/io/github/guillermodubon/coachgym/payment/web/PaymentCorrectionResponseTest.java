package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentRefundDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCorrectionResponseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-10T20:00:00Z");

    @Test
    void mapsVoidWithoutRefund() {
        PaymentCorrectionResponse response = PaymentCorrectionResponse.from(
                PaymentCorrectionDetails.voided(
                        UUID.randomUUID(), "PAY-000001", "Registered twice",
                        NOW, UUID.randomUUID(), 1));

        assertThat(response.currentStatus().name()).isEqualTo("VOIDED");
        assertThat(response.refund()).isNull();
    }

    @Test
    void mapsFullRefund() {
        UUID paymentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PaymentRefundDetails refund = new PaymentRefundDetails(
                UUID.randomUUID(), paymentId, new BigDecimal("25.00"),
                "USD", "Approved refund", "REF-001", NOW, actorId);

        PaymentCorrectionResponse response = PaymentCorrectionResponse.from(
                PaymentCorrectionDetails.refunded(
                        paymentId, "PAY-000002", "Approved refund",
                        NOW, actorId, 1, refund));

        assertThat(response.currentStatus().name()).isEqualTo("REFUNDED");
        assertThat(response.refund().amount()).isEqualByComparingTo("25.00");
        assertThat(response.refund().currency()).isEqualTo("USD");
    }
}
