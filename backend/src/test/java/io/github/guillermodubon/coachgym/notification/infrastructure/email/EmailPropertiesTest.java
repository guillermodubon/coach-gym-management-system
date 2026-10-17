package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailPropertiesTest {

    @Test
    void disabledDeliveryNeedsNoProviderCredentials() {
        assertThat(properties(false, "not-an-address", 0, 0, null, null)
                .isValidWhenEnabled()).isTrue();
    }

    @Test
    void enabledDeliveryValidatesProviderNeutralIdentityAndBounds() {
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024,
                200, 3, Duration.ofMinutes(15)).isValidWhenEnabled()).isTrue();
        assertThat(properties(true, "invalid", 10 * 1024 * 1024,
                200, 3, Duration.ofMinutes(15)).isValidWhenEnabled()).isFalse();
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024 + 1,
                200, 3, Duration.ofMinutes(15)).isValidWhenEnabled()).isFalse();
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024,
                201, 3, Duration.ofMinutes(15)).isValidWhenEnabled()).isFalse();
    }

    @Test
    void appliesDefaultsAndValidatesDeliveryLifecycleBounds() {
        EmailProperties defaults = properties(true, "no-reply@example.test", 0, 0, null, null);
        assertThat(defaults.maxAttachmentBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(defaults.maxSubjectLength()).isEqualTo(200);
        assertThat(defaults.maxRetryCount()).isEqualTo(3);
        assertThat(defaults.stalePendingThreshold()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024,
                200, 10, Duration.ofHours(24)).isValidWhenEnabled()).isTrue();
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024,
                200, 11, Duration.ofHours(24)).isValidWhenEnabled()).isFalse();
        assertThat(properties(true, "no-reply@example.test", 10 * 1024 * 1024,
                200, 3, Duration.ofHours(24).plusMillis(1)).isValidWhenEnabled()).isFalse();
    }

    @Test
    void textOutputDoesNotExposeConfiguredAddresses() {
        EmailProperties properties = properties(
                true, "private-sender@example.test", 0, 0, null, null);

        assertThat(properties.toString())
                .contains("fromAddressPresent=true")
                .doesNotContain("private-sender@example.test");
    }

    private static EmailProperties properties(
            boolean enabled,
            String fromAddress,
            int maxAttachmentBytes,
            int maxSubjectLength,
            Integer maxRetryCount,
            Duration stalePendingThreshold) {
        return new EmailProperties(
                enabled,
                "Coach Gym",
                "v1",
                fromAddress,
                "Coach Gym",
                null,
                maxAttachmentBytes,
                maxSubjectLength,
                maxRetryCount,
                stalePendingThreshold);
    }
}
