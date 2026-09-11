package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class OfficialStripeSdkClientTest {

    private static final String ATTEMPT_ID = "00000000-0000-0000-0000-000000000751";
    private static final long EVENT_TIMESTAMP = 1_800_000_000L;

    @Test
    void verifiesValidSignatureAndMapsCheckoutPayloadOffline() {
        String payload = """
                {"id":"evt_test_1","object":"event","api_version":"2024-06-20","created":1800000000,
                "data":{"object":{"id":"cs_test_1","object":"checkout.session","amount_total":2500,
                "currency":"usd","payment_intent":"pi_test_1","metadata":{"payment_attempt_id":"%s"}}},
                "livemode":false,"pending_webhooks":1,"type":"checkout.session.completed"}
                """.formatted(ATTEMPT_ID).replaceAll("\\s+", "");
        OfficialStripeSdkClient client = new OfficialStripeSdkClient(properties());

        StripeVerifiedEvent event = client.verifyWebhook(new StripeWebhookRequest(
                payload.getBytes(StandardCharsets.UTF_8), sign(payload, EVENT_TIMESTAMP),
                java.time.Instant.ofEpochSecond(EVENT_TIMESTAMP)));

        assertThat(event.eventReference()).isEqualTo("evt_test_1");
        assertThat(event.eventType()).isEqualTo("checkout.session.completed");
        assertThat(event.paymentAttemptId().toString()).isEqualTo(ATTEMPT_ID);
        assertThat(event.checkoutReference()).isEqualTo("cs_test_1");
        assertThat(event.providerPaymentReference()).isEqualTo("pi_test_1");
        assertThat(event.amount()).isEqualByComparingTo("25.00");
        assertThat(event.currency()).isEqualTo("usd");
    }

    @Test
    void rejectsStaleAndMalformedSignaturesWithoutProviderDetails() {
        String payload = "{\"id\":\"evt_test_2\",\"object\":\"event\"}";
        OfficialStripeSdkClient client = new OfficialStripeSdkClient(properties());

        assertThatThrownBy(() -> client.verifyWebhook(new StripeWebhookRequest(
                payload.getBytes(StandardCharsets.UTF_8), sign(payload, EVENT_TIMESTAMP - 600),
                java.time.Instant.ofEpochSecond(EVENT_TIMESTAMP))))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_SIGNATURE);
        assertThatThrownBy(() -> client.verifyWebhook(new StripeWebhookRequest(
                payload.getBytes(StandardCharsets.UTF_8), "malformed", java.time.Instant.now())))
                .isInstanceOf(PaymentProviderException.class)
                .extracting("failureCode").isEqualTo(PaymentProviderFailureCode.INVALID_SIGNATURE);
    }

    private static StripeProperties properties() {
        return new StripeProperties(true, true, "sk_test_placeholder", "whsec_placeholder",
                "https://example.test/success", "https://example.test/cancel",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(5), 262_144, 1_024);
    }

    private static String sign(String payload, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("whsec_placeholder".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
