package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCorrectionDetailsTest {

    private static final Instant CORRECTED_AT =
            Instant.parse("2026-09-09T15:00:00Z");

    @Test
    void createsVoidedCorrectionWithoutRefund() {
        PaymentCorrectionDetails correction = PaymentCorrectionDetails.voided(
                UUID.randomUUID(), "PAY-000001", "Registered twice",
                CORRECTED_AT, UUID.randomUUID(), 1L);

        assertThat(correction.correctionType())
                .isEqualTo(PaymentCorrectionType.VOID);
        assertThat(correction.previousStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(correction.currentStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(correction.refund()).isNull();
    }

    @Test
    void createsRefundedCorrectionWithMatchingRefund() {
        UUID paymentId = UUID.randomUUID();
        PaymentRefundDetails refund = new PaymentRefundDetails(
                UUID.randomUUID(), paymentId, new BigDecimal("25.00"),
                "USD", "Approved refund", "REF-001", CORRECTED_AT,
                UUID.randomUUID());

        PaymentCorrectionDetails correction = PaymentCorrectionDetails.refunded(
                paymentId, "PAY-000002", "Approved refund", CORRECTED_AT,
                UUID.randomUUID(), 1L, refund);

        assertThat(correction.correctionType())
                .isEqualTo(PaymentCorrectionType.REFUND);
        assertThat(correction.currentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(correction.refund()).isSameAs(refund);
    }

    @Test
    void rejectsInvalidCorrectionCombinations() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> new PaymentCorrectionDetails(
                paymentId, "PAY-1", PaymentCorrectionType.VOID,
                PaymentStatus.PAID, PaymentStatus.REFUNDED,
                "Reason", CORRECTED_AT, UUID.randomUUID(), 1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaymentCorrectionDetails(
                paymentId, "PAY-1", PaymentCorrectionType.REFUND,
                PaymentStatus.PAID, PaymentStatus.REFUNDED,
                "Reason", CORRECTED_AT, UUID.randomUUID(), 1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> PaymentCorrectionDetails.voided(
                paymentId, "PAY-1", "Reason", CORRECTED_AT,
                UUID.randomUUID(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
