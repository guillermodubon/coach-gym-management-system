package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptProviderContractTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000711");

    @Test
    void acceptsOnlyServerDerivedCheckoutValues() {
        ProviderCheckoutRequest request = new ProviderCheckoutRequest(
                ATTEMPT_ID,
                PaymentProvider.STRIPE,
                new BigDecimal("25.00"),
                "usd",
                URI.create("https://example.test/payments/success"),
                URI.create("https://example.test/payments/cancel"));

        assertThat(request.currency()).isEqualTo("USD");
        assertThat(CardCheckoutGateway.class.isInterface()).isTrue();
    }

    @Test
    void rejectsInvalidAmountCurrencyAndProviderReferences() {
        assertThatThrownBy(() -> new ProviderCheckoutRequest(
                ATTEMPT_ID,
                PaymentProvider.STRIPE,
                BigDecimal.ZERO,
                "USD",
                URI.create("https://example.test/success"),
                URI.create("https://example.test/cancel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderCheckoutRequest(
                ATTEMPT_ID,
                PaymentProvider.STRIPE,
                new BigDecimal("25.00"),
                "US",
                URI.create("https://example.test/success"),
                URI.create("https://example.test/cancel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderCheckout(
                PaymentProvider.STRIPE,
                " ",
                URI.create("https://example.test/checkout"),
                Instant.parse("2026-09-10T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsVerifiedProviderDataInsideTheApplicationPackage() {
        assertThat(VerifiedPaymentProviderEvent.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.payment.application");
        assertThatThrownBy(() -> new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE,
                "event-1",
                PaymentProviderEventType.CHECKOUT_COMPLETED,
                ATTEMPT_ID,
                "checkout-1",
                null,
                new BigDecimal("25.00"),
                "USD",
                Instant.parse("2026-09-10T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
