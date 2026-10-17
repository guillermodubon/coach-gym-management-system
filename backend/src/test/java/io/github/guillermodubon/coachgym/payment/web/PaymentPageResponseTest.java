package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPageResponseTest {

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

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    @Test
    void mapsPageMetadataAndItems() {
        PaymentDetails details = new PaymentDetails(
                PAYMENT_ID, "PAY-000001", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH, PaymentStatus.PAID,
                null, NOW, ACTOR_ID, NOW, NOW, 0L);

        PaymentPage page = new PaymentPage(
                List.of(details), 0, 25, 1L, 1);

        PaymentPageResponse response = PaymentPageResponse.from(page);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(PAYMENT_ID);
        assertThat(response.items().get(0).paymentCode())
                .isEqualTo("PAY-000001");
    }

    @Test
    void mapsEmptyPage() {
        PaymentPage page = new PaymentPage(List.of(), 0, 25, 0L, 0);

        PaymentPageResponse response = PaymentPageResponse.from(page);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }
}
