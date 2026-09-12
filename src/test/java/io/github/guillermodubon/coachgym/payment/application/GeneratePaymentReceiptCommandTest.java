package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratePaymentReceiptCommandTest {

    @Test
    void containsOnlyThePaymentIdentifierControlledByTheServerWorkflow() {
        assertThat(GeneratePaymentReceiptCommand.class.isRecord()).isTrue();
        assertThat(GeneratePaymentReceiptCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("paymentId");

        UUID paymentId = UUID.randomUUID();
        assertThat(new GeneratePaymentReceiptCommand(paymentId).paymentId())
                .isEqualTo(paymentId);
    }

    @Test
    void rejectsMissingPaymentIdentifierWithSafeValidationException() {
        assertThatThrownBy(() -> new GeneratePaymentReceiptCommand(null))
                .isInstanceOf(PaymentReceiptValidationException.class)
                .hasMessage("Payment id is required.");
    }
}
