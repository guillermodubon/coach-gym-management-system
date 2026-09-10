package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentStatusHistoryDetailsTest {

    @Test
    void representsPaidToVoidedTransition() {
        PaymentStatusHistoryDetails history = new PaymentStatusHistoryDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.PAID,
                PaymentStatus.VOIDED,
                " Registered twice ",
                Instant.parse("2026-09-09T15:00:00Z"),
                UUID.randomUUID());

        assertThat(history.reason()).isEqualTo("Registered twice");
        assertThat(history.previousStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(history.newStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void rejectsTransitionToSameStatus() {
        assertThatThrownBy(() -> new PaymentStatusHistoryDetails(
                UUID.randomUUID(), UUID.randomUUID(),
                PaymentStatus.PAID, PaymentStatus.PAID,
                "No change", Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
