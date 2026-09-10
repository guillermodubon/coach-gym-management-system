package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoidPaymentCommandTest {

    @Test
    void normalizesReasonAndPreservesExpectedVersion() {
        UUID paymentId = UUID.randomUUID();
        VoidPaymentCommand command = new VoidPaymentCommand(
                paymentId,
                "  Registered twice  ",
                3L);

        assertThat(command.paymentId()).isEqualTo(paymentId);
        assertThat(command.reason()).isEqualTo("Registered twice");
        assertThat(command.expectedVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsMissingShortOversizedReasonAndInvalidVersion() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> new VoidPaymentCommand(null, "Valid reason", 0))
                .isInstanceOf(PaymentCorrectionValidationException.class);
        assertThatThrownBy(() -> new VoidPaymentCommand(paymentId, " ", 0))
                .isInstanceOf(PaymentCorrectionValidationException.class);
        assertThatThrownBy(() -> new VoidPaymentCommand(paymentId, "No", 0))
                .isInstanceOf(PaymentCorrectionValidationException.class);
        assertThatThrownBy(() -> new VoidPaymentCommand(
                paymentId, "x".repeat(2001), 0))
                .isInstanceOf(PaymentCorrectionValidationException.class);
        assertThatThrownBy(() -> new VoidPaymentCommand(
                paymentId, "Valid reason", -1))
                .isInstanceOf(PaymentCorrectionValidationException.class);
    }
}
