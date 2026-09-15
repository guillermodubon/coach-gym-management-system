package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StripePropertiesTest {

    @Test
    void disabledStripeDoesNotRequireSecrets() {
        assertThat(properties(false, false, "", "").isValidWhenEnabled()).isTrue();
    }

    @Test
    void enabledStripeRequiresSandboxAndSafeBoundedConfiguration() {
        assertThat(properties(true, true, "sk_test_key", "whsec_test").isValidWhenEnabled()).isTrue();
        assertThat(properties(true, false, "sk_test_key", "whsec_test").isValidWhenEnabled()).isFalse();
        assertThat(properties(true, true, "sk_live_key", "whsec_test").isValidWhenEnabled()).isFalse();
        assertThat(properties(true, true, "pk_test_key", "whsec_test").isValidWhenEnabled()).isFalse();
        assertThat(properties(true, true, "sk_test_key", "signing-secret").isValidWhenEnabled()).isFalse();
        assertThat(properties(true, true, "sk_test_key", "").isValidWhenEnabled()).isFalse();
    }

    @Test
    void rejectsUnboundedTimeoutsAndLimits() {
        StripeProperties base = properties(true, true, "sk_test_key", "whsec_test");
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", base.successUrl(),
                base.cancelUrl(), Duration.ZERO, base.readTimeout(), base.webhookTolerance(),
                base.maxWebhookPayloadBytes(), base.maxSignatureHeaderLength()).isValidWhenEnabled()).isFalse();
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", base.successUrl(),
                base.cancelUrl(), base.connectTimeout(), base.readTimeout(), base.webhookTolerance(),
                2_000_000, base.maxSignatureHeaderLength()).isValidWhenEnabled()).isFalse();
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", base.successUrl(),
                base.cancelUrl(), base.connectTimeout(), Duration.ofMinutes(3), base.webhookTolerance(),
                base.maxWebhookPayloadBytes(), base.maxSignatureHeaderLength()).isValidWhenEnabled()).isFalse();
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", base.successUrl(),
                base.cancelUrl(), base.connectTimeout(), base.readTimeout(), Duration.ZERO,
                base.maxWebhookPayloadBytes(), base.maxSignatureHeaderLength()).isValidWhenEnabled()).isFalse();
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", base.successUrl(),
                base.cancelUrl(), base.connectTimeout(), base.readTimeout(), base.webhookTolerance(),
                base.maxWebhookPayloadBytes(), 0).isValidWhenEnabled()).isFalse();
        assertThat(new StripeProperties(true, true, "sk_test_key", "whsec_test", "relative", base.cancelUrl(),
                base.connectTimeout(), base.readTimeout(), base.webhookTolerance(),
                base.maxWebhookPayloadBytes(), base.maxSignatureHeaderLength()).isValidWhenEnabled()).isFalse();
    }

    private static StripeProperties properties(
            boolean enabled, boolean sandbox, String key, String webhookSecret) {
        return new StripeProperties(enabled, sandbox, key, webhookSecret,
                "https://example.test/success", "https://example.test/cancel",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(5),
                262_144, 1_024);
    }
}
