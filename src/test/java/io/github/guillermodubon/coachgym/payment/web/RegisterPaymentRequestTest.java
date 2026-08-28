package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.application.RegisterPaymentCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterPaymentRequestTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    @Test
    void mapsAllFieldsToCommand() {
        RegisterPaymentRequest request = new RegisterPaymentRequest(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                "REF-001", PAID_AT);

        RegisterPaymentCommand cmd = request.toCommand();

        assertThat(cmd.clientId()).isEqualTo(CLIENT_ID);
        assertThat(cmd.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(cmd.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(cmd.amount()).isEqualByComparingTo("25.00");
        assertThat(cmd.currency()).isEqualTo("USD");
        assertThat(cmd.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(cmd.externalReference()).isEqualTo("REF-001");
        assertThat(cmd.paidAt()).isEqualTo(PAID_AT);
    }

    @Test
    void nullExternalReferenceBecomesNullInCommand() {
        RegisterPaymentRequest request = new RegisterPaymentRequest(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                null, PAID_AT);

        assertThat(request.toCommand().externalReference()).isNull();
    }

    @Test
    void blankExternalReferenceBecomesNullInCommand() {
        RegisterPaymentRequest request = new RegisterPaymentRequest(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                "   ", PAID_AT);

        assertThat(request.toCommand().externalReference()).isNull();
    }

    @Test
    void trimsExternalReferenceInCommand() {
        RegisterPaymentRequest request = new RegisterPaymentRequest(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                "  REF-001  ", PAID_AT);

        assertThat(request.toCommand().externalReference()).isEqualTo("REF-001");
    }
}
