package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRefundDetailsTest {

    @Test
    void createsNormalizedFullRefundDetails() {
        PaymentRefundDetails refund = new PaymentRefundDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("25.00"),
                " usd ",
                " Customer refund ",
                " REF-001 ",
                Instant.parse("2026-09-09T15:00:00Z"),
                UUID.randomUUID());

        assertThat(refund.amount()).isEqualByComparingTo("25.00");
        assertThat(refund.currency()).isEqualTo("USD");
        assertThat(refund.reason()).isEqualTo("Customer refund");
        assertThat(refund.externalReference()).isEqualTo("REF-001");
    }

    @Test
    void acceptsMissingExternalReference() {
        PaymentRefundDetails refund = validRefund(null);
        assertThat(refund.externalReference()).isNull();
    }

    @Test
    void rejectsInvalidAmountCurrencyReasonAndActor() {
        assertThatThrownBy(() -> new PaymentRefundDetails(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO,
                "USD", "Reason", null, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaymentRefundDetails(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                "US", "Reason", null, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaymentRefundDetails(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                "USD", " ", null, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaymentRefundDetails(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                "USD", "Reason", null, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PaymentRefundDetails validRefund(String reference) {
        return new PaymentRefundDetails(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.00"),
                "USD", "Approved refund", reference,
                Instant.parse("2026-09-09T15:00:00Z"), UUID.randomUUID());
    }
}
