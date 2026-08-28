package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentResponseTest {

    private static final UUID PAYMENT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    @Test
    void mapsAllFieldsFromDetails() {
        PaymentDetails details = new PaymentDetails(
                PAYMENT_ID, "PAY-000001", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH, PaymentStatus.PAID,
                null, PAID_AT, ACTOR_ID, NOW, NOW, 0L);

        PaymentResponse response = PaymentResponse.from(details);

        assertThat(response.id()).isEqualTo(PAYMENT_ID);
        assertThat(response.paymentCode()).isEqualTo("PAY-000001");
        assertThat(response.clientId()).isEqualTo(CLIENT_ID);
        assertThat(response.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(response.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.externalReference()).isNull();
        assertThat(response.paidAt()).isEqualTo(PAID_AT);
        assertThat(response.registeredByUserId()).isEqualTo(ACTOR_ID);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.version()).isZero();
    }

    @Test
    void mapsExternalReferenceWhenPresent() {
        PaymentDetails details = new PaymentDetails(
                PAYMENT_ID, "PAY-000001", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CARD, PaymentStatus.PAID,
                "REF-001", PAID_AT, ACTOR_ID, NOW, NOW, 0L);

        PaymentResponse response = PaymentResponse.from(details);

        assertThat(response.externalReference()).isEqualTo("REF-001");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
    }
}
