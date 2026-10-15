package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReceiptPolicyTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000903");

    @Test
    void allowsGenerationOnlyForConfirmedPaidPayments() {
        assertThat(PaymentReceiptPolicy.isGenerationEligible(PaymentStatus.PAID)).isTrue();
        assertThatCode(() -> PaymentReceiptPolicy.requireGenerationAllowed(
                PAYMENT_ID, PaymentStatus.PAID))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsVoidedAndRefundedPaymentsWithStateConflict() {
        for (PaymentStatus status : new PaymentStatus[] {
                PaymentStatus.VOIDED, PaymentStatus.REFUNDED}) {
            assertThat(PaymentReceiptPolicy.isGenerationEligible(status)).isFalse();
            assertThatThrownBy(() -> PaymentReceiptPolicy.requireGenerationAllowed(
                    PAYMENT_ID, status))
                    .isInstanceOf(PaymentReceiptStateConflictException.class)
                    .satisfies(error -> {
                        PaymentReceiptStateConflictException conflict =
                                (PaymentReceiptStateConflictException) error;
                        assertThat(conflict.paymentId()).isEqualTo(PAYMENT_ID);
                        assertThat(conflict.currentStatus()).isEqualTo(status);
                    });
        }
    }

    @Test
    void rejectsMissingIdentifiersAndStatusesAsValidationErrors() {
        assertThatThrownBy(() -> PaymentReceiptPolicy.requireGenerationAllowed(
                null, PaymentStatus.PAID))
                .isInstanceOf(PaymentReceiptValidationException.class);
        assertThatThrownBy(() -> PaymentReceiptPolicy.requireGenerationAllowed(
                PAYMENT_ID, null))
                .isInstanceOf(PaymentReceiptValidationException.class);
    }
}
