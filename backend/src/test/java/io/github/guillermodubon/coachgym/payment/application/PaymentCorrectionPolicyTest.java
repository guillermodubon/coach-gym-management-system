package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCorrectionPolicyTest {

    private final UUID paymentId = UUID.randomUUID();

    @Test
    void allowsOnlyPaidToVoidedAndPaidToRefunded() {
        assertThatCode(() -> PaymentCorrectionPolicy.requireVoidAllowed(
                paymentId, PaymentStatus.PAID))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentCorrectionPolicy.requireRefundAllowed(
                paymentId, PaymentStatus.PAID))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryTransitionFromFinalStates() {
        for (PaymentStatus current : new PaymentStatus[] {
                PaymentStatus.VOIDED,
                PaymentStatus.REFUNDED}) {
            for (PaymentStatus requested : PaymentStatus.values()) {
                assertThatThrownBy(() ->
                        PaymentCorrectionPolicy.requireTransitionAllowed(
                                paymentId, current, requested))
                        .isInstanceOf(
                                PaymentCorrectionStateConflictException.class);
            }
        }
    }

    @Test
    void rejectsPaidToPaidAndMissingStatuses() {
        assertThatThrownBy(() ->
                PaymentCorrectionPolicy.requireTransitionAllowed(
                        paymentId, PaymentStatus.PAID, PaymentStatus.PAID))
                .isInstanceOf(PaymentCorrectionStateConflictException.class);
        assertThatThrownBy(() ->
                PaymentCorrectionPolicy.requireTransitionAllowed(
                        paymentId, null, PaymentStatus.VOIDED))
                .isInstanceOf(PaymentCorrectionValidationException.class);
        assertThatThrownBy(() ->
                PaymentCorrectionPolicy.requireTransitionAllowed(
                        paymentId, PaymentStatus.PAID, null))
                .isInstanceOf(PaymentCorrectionValidationException.class);
    }

    @Test
    void differentiatesOptimisticVersionConflict() {
        assertThatCode(() -> PaymentCorrectionPolicy.requireExpectedVersion(
                paymentId, 4L, 4L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PaymentCorrectionPolicy.requireExpectedVersion(
                paymentId, 3L, 4L))
                .isInstanceOf(PaymentCorrectionVersionConflictException.class)
                .satisfies(error -> {
                    PaymentCorrectionVersionConflictException conflict =
                            (PaymentCorrectionVersionConflictException) error;
                    org.assertj.core.api.Assertions.assertThat(
                            conflict.expectedVersion()).isEqualTo(3L);
                    org.assertj.core.api.Assertions.assertThat(
                            conflict.currentVersion()).isEqualTo(4L);
                });
    }
}
