package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckout;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckoutCancellationRequest;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckoutRequest;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StripeCheckoutGatewayTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000731");

    @Test
    void mapsServerOwnedCheckoutDataToMinorUnitsWithoutNetwork() {
        CapturingClient client = new CapturingClient();
        StripeCheckoutGateway gateway = new StripeCheckoutGateway(client, properties());
        ProviderCheckout result = gateway.createCheckout(new ProviderCheckoutRequest(
                ATTEMPT_ID, PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD",
                URI.create("https://example.test/success"), URI.create("https://example.test/cancel")));

        assertThat(client.request.amountMinor()).isEqualTo(2500);
        assertThat(client.request.currency()).isEqualTo("usd");
        assertThat(client.request.attemptId()).isEqualTo(ATTEMPT_ID.toString());
        assertThat(result.checkoutReference()).isEqualTo("cs_test_1");
    }

    @Test
    void rejectsUnsupportedPrecision() {
        StripeCheckoutGateway gateway = new StripeCheckoutGateway(new CapturingClient(), properties());
        assertThatThrownBy(() -> gateway.createCheckout(new ProviderCheckoutRequest(
                ATTEMPT_ID, PaymentProvider.STRIPE, new BigDecimal("25.001"), "USD",
                URI.create("https://example.test/success"), URI.create("https://example.test/cancel"))))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_RESPONSE);
    }

    @Test
    void cancellationUsesProviderReferenceOnly() {
        CapturingClient client = new CapturingClient();
        new StripeCheckoutGateway(client, properties()).cancelCheckout(
                new ProviderCheckoutCancellationRequest(PaymentProvider.STRIPE, "cs_test_1"));
        assertThat(client.cancelledReference).isEqualTo("cs_test_1");
    }

    @Test
    void preservesStableProviderFailureFromSdkSeam() {
        StripeSdkClient unavailable = new CapturingClient() {
            @Override
            public StripeCheckoutResult createCheckout(StripeCheckoutCreationRequest request) {
                throw new PaymentProviderException(PaymentProviderFailureCode.TIMEOUT);
            }
        };

        assertThatThrownBy(() -> new StripeCheckoutGateway(unavailable, properties())
                .createCheckout(validRequest()))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.TIMEOUT);
    }

    @Test
    void rejectsCallerSuppliedRedirectTargets() {
        ProviderCheckoutRequest request = new ProviderCheckoutRequest(
                ATTEMPT_ID, PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD",
                URI.create("https://attacker.test/success"), URI.create("https://example.test/cancel"));

        assertThatThrownBy(() -> new StripeCheckoutGateway(new CapturingClient(), properties())
                .createCheckout(request))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_RESPONSE);
    }

    private static ProviderCheckoutRequest validRequest() {
        return new ProviderCheckoutRequest(
                ATTEMPT_ID, PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD",
                URI.create("https://example.test/success"), URI.create("https://example.test/cancel"));
    }

    private static StripeProperties properties() {
        return new StripeProperties(true, true, "sk_test_key", "whsec_test",
                "https://example.test/success", "https://example.test/cancel",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(5), 262_144, 1_024);
    }

    private static class CapturingClient implements StripeSdkClient {
        private StripeCheckoutCreationRequest request;
        private String cancelledReference;

        @Override
        public StripeCheckoutResult createCheckout(StripeCheckoutCreationRequest request) {
            this.request = request;
            return new StripeCheckoutResult("cs_test_1", URI.create("https://checkout.test/session"),
                    Instant.parse("2026-09-10T17:00:00Z"));
        }

        @Override
        public void expireCheckout(String checkoutReference) {
            cancelledReference = checkoutReference;
        }

        @Override
        public StripeVerifiedEvent verifyWebhook(StripeWebhookRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
