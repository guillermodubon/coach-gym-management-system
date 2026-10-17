package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentStatusHistoryPageTest {

    @Test
    void defensivelyCopiesContentAndCalculatesNavigation() {
        PaymentStatusHistoryDetails entry = entry();
        java.util.ArrayList<PaymentStatusHistoryDetails> mutable =
                new java.util.ArrayList<>(List.of(entry));

        PaymentStatusHistoryPage page = new PaymentStatusHistoryPage(
                mutable, 0, 25, 1, 1);
        mutable.clear();

        assertThat(page.content()).containsExactly(entry);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
        assertThat(page.empty()).isFalse();
        assertThatThrownBy(() -> page.content().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void representsEmptyHistory() {
        PaymentStatusHistoryPage page = new PaymentStatusHistoryPage(
                List.of(), 0, 25, 0, 0);

        assertThat(page.empty()).isTrue();
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    void rejectsInvalidPaginationMetadata() {
        assertThatThrownBy(() -> new PaymentStatusHistoryPage(
                List.of(), -1, 25, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatusHistoryPage(
                List.of(), 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatusHistoryPage(
                List.of(), 0, 101, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatusHistoryPage(
                List.of(), 0, 25, 26, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PaymentStatusHistoryDetails entry() {
        return new PaymentStatusHistoryDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.PAID,
                PaymentStatus.VOIDED,
                "Registered twice",
                Instant.parse("2026-09-10T12:00:00Z"),
                UUID.randomUUID());
    }
}
