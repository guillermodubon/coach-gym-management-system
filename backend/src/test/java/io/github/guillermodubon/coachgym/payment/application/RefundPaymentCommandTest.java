package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RefundPaymentCommandTest {

    @Test
    void normalizesReasonAndOptionalExternalReference() {
        UUID paymentId = UUID.randomUUID();
        RefundPaymentCommand command = new RefundPaymentCommand(
                paymentId,
                "  Approved customer refund  ",
                "  REF-2026-001  ",
                2L);

        assertThat(command.paymentId()).isEqualTo(paymentId);
        assertThat(command.reason()).isEqualTo("Approved customer refund");
        assertThat(command.externalReference()).isEqualTo("REF-2026-001");
        assertThat(command.expectedVersion()).isEqualTo(2L);
    }

    @Test
    void convertsBlankExternalReferenceToNull() {
        RefundPaymentCommand command = new RefundPaymentCommand(
                UUID.randomUUID(),
                "Approved refund",
                "   ",
                0L);

        assertThat(command.externalReference()).isNull();
    }

    @Test
    void rejectsOversizedExternalReference() {
        assertThatThrownBy(() -> new RefundPaymentCommand(
                UUID.randomUUID(),
                "Approved refund",
                "x".repeat(129),
                0L))
                .isInstanceOf(PaymentCorrectionValidationException.class);
    }

    @Test
    void doesNotAcceptAmountCurrencyStatusActorOrProviderFields() {
        String components = Arrays.stream(
                        RefundPaymentCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain("amount")
                .doesNotContain("currency")
                .doesNotContain("status")
                .doesNotContain("actor")
                .doesNotContain("timestamp")
                .doesNotContain("stripe")
                .doesNotContain("paymentintent");
    }
}
