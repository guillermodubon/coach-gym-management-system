package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStateConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReceiptEventAndExceptionTest {

    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000907");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000908");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000909");

    @Test
    void normalizesThePrivacySafeGeneratedEvent() {
        PaymentReceiptGenerated event = new PaymentReceiptGenerated(
                RECEIPT_ID,
                " REC-0001 ",
                PAYMENT_ID,
                " PAY-0001 ",
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                " usd ",
                ACTOR_ID,
                " admin ",
                true,
                Instant.parse("2026-09-11T10:05:00Z"));

        assertThat(event.receiptNumber()).isEqualTo("REC-0001");
        assertThat(event.paymentCode()).isEqualTo("PAY-0001");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.actorIdentifier()).isEqualTo("admin");
        assertThat(event.testMode()).isTrue();
    }

    @Test
    void exceptionMessagesDoNotExposeIdentifiersOrInfrastructureDetails() {
        assertThat(new PaymentReceiptStateConflictException(
                PAYMENT_ID, PaymentStatus.VOIDED))
                .hasMessage("A receipt can only be generated for a confirmed paid payment.")
                .hasMessageNotContaining(PAYMENT_ID.toString());

        assertThat(new PaymentReceiptNotFoundException(RECEIPT_ID))
                .hasMessage("Payment receipt was not found.")
                .hasMessageNotContaining(RECEIPT_ID.toString());

        assertThat(new PaymentReceiptNotFoundException(RECEIPT_ID).resourceId())
                .isEqualTo(RECEIPT_ID);
    }
}
