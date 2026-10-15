package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StripeWebhookVerifierTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000741");

    @Test
    void mapsVerifiedCompletedEventToProviderNeutralContract() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new WebhookClient(new StripeVerifiedEvent("evt_1", "checkout.session.completed", ATTEMPT_ID,
                        "cs_1", "pi_1", new java.math.BigDecimal("25.00"), "USD",
                        Instant.parse("2026-09-10T16:00:00Z"))),
                properties(), Clock.fixed(Instant.parse("2026-09-10T16:01:00Z"), ZoneOffset.UTC));

        VerifiedPaymentProviderEvent event = verifier.verify(PaymentProvider.STRIPE,
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "t=1,v1=signature");

        assertThat(event.eventType()).isEqualTo(PaymentProviderEventType.CHECKOUT_COMPLETED);
        assertThat(event.paymentAttemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(event.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsOversizedOrMalformedHeadersBeforeSdkInvocation() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new WebhookClient(null) {
                    @Override
                    public StripeVerifiedEvent verifyWebhook(StripeWebhookRequest request) {
                        throw new AssertionError("SDK must not be called");
                    }
                }, properties(), Clock.systemUTC());

        assertThatThrownBy(() -> verifier.verify(PaymentProvider.STRIPE, new byte[262_145], "t=1,v1=x"))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_SIGNATURE);
        assertThatThrownBy(() -> verifier.verify(PaymentProvider.STRIPE, new byte[0], ""))
                .isInstanceOf(PaymentProviderException.class);
        assertThatThrownBy(() -> verifier.verify(PaymentProvider.STRIPE, new byte[] {1}, "x".repeat(1_025)))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_SIGNATURE);
    }

    @Test
    void mapsFailedEventsWithoutInventingCheckoutReference() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new WebhookClient(new StripeVerifiedEvent("evt_2", "payment_intent.payment_failed", ATTEMPT_ID,
                        null, null, null, null, Instant.now())), properties(), Clock.systemUTC());

        assertThat(verifier.verify(PaymentProvider.STRIPE, new byte[] {1}, "valid")
                .eventType()).isEqualTo(PaymentProviderEventType.PAYMENT_FAILED);
    }

    @Test
    void rejectsUnknownVerifiedEventTypesSafely() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new WebhookClient(new StripeVerifiedEvent("evt_3", "customer.created", ATTEMPT_ID,
                        null, null, null, null, Instant.now())), properties(), Clock.systemUTC());
        assertThatThrownBy(() -> verifier.verify(PaymentProvider.STRIPE, new byte[] {1}, "valid"))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.UNSUPPORTED_EVENT);
    }

    private static StripeProperties properties() {
        return new StripeProperties(true, true, "sk_test_key", "whsec_test",
                "https://example.test/success", "https://example.test/cancel",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(5), 262_144, 1_024);
    }

    private static class WebhookClient implements StripeSdkClient {
        private final StripeVerifiedEvent event;

        WebhookClient(StripeVerifiedEvent event) {
            this.event = event;
        }

        @Override
        public StripeCheckoutResult createCheckout(StripeCheckoutCreationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void expireCheckout(String checkoutReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StripeVerifiedEvent verifyWebhook(StripeWebhookRequest request) {
            return event;
        }
    }
}
