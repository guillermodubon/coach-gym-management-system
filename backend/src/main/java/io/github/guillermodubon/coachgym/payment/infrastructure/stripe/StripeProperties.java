package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.payment.stripe")
public record StripeProperties(
        boolean enabled,
        boolean sandbox,
        String secretKey,
        String webhookSigningSecret,
        String successUrl,
        String cancelUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration webhookTolerance,
        int maxWebhookPayloadBytes,
        int maxSignatureHeaderLength) {

    @Override
    public String toString() {
        return "StripeProperties[enabled=" + enabled
                + ", sandbox=" + sandbox
                + ", secretKeyPresent=" + hasText(secretKey)
                + ", webhookSigningSecretPresent=" + hasText(webhookSigningSecret)
                + ", successUrlPresent=" + hasText(successUrl)
                + ", cancelUrlPresent=" + hasText(cancelUrl)
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", webhookTolerance=" + webhookTolerance
                + ", maxWebhookPayloadBytes=" + maxWebhookPayloadBytes
                + ", maxSignatureHeaderLength=" + maxSignatureHeaderLength
                + ']';
    }

    @AssertTrue(message = "Stripe Test Mode configuration is invalid")
    public boolean isValidWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return sandbox
                && hasText(secretKey)
                && hasText(webhookSigningSecret)
                && secretKey.strip().startsWith("sk_test_")
                && !secretKey.strip().startsWith("sk_live_")
                && webhookSigningSecret.strip().startsWith("whsec_")
                && validAbsoluteUrl(successUrl)
                && validAbsoluteUrl(cancelUrl)
                && positiveBounded(connectTimeout, Duration.ofMinutes(1))
                && positiveBounded(readTimeout, Duration.ofMinutes(2))
                && positiveBounded(webhookTolerance, Duration.ofHours(1))
                && maxWebhookPayloadBytes >= 1_024
                && maxWebhookPayloadBytes <= 1_048_576
                && maxSignatureHeaderLength >= 64
                && maxSignatureHeaderLength <= 8_192;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validAbsoluteUrl(String value) {
        try {
            if (!hasText(value)) {
                return false;
            }
            java.net.URI uri = java.net.URI.create(value);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative() && value.compareTo(maximum) <= 0;
    }
}
