package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCorrectionExceptionContractTest {

    @Test
    void stateConflictRetainsOnlyOperationalIdentifiersAndStatuses() {
        UUID paymentId = UUID.randomUUID();
        PaymentCorrectionStateConflictException exception =
                new PaymentCorrectionStateConflictException(
                        paymentId,
                        PaymentStatus.VOIDED,
                        PaymentStatus.REFUNDED);

        assertThat(exception.paymentId()).isEqualTo(paymentId);
        assertThat(exception.currentStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(exception.requestedStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(exception.getMessage())
                .doesNotContain(paymentId.toString());
    }

    @Test
    void refundConflictRetainsPaymentIdWithoutExternalReferences() {
        UUID paymentId = UUID.randomUUID();
        PaymentRefundConflictException exception =
                new PaymentRefundConflictException(paymentId);

        assertThat(exception.paymentId()).isEqualTo(paymentId);
        assertThat(exception.getMessage())
                .doesNotContain(paymentId.toString())
                .doesNotContain("Stripe")
                .doesNotContain("card");
    }
}
