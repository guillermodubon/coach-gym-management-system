package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.infrastructure.resend.ResendProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

class EmailHealthIndicatorTest {

    @Test
    void disabledEmailIsReadyWithoutContactingMailpitOrResend() {
        EmailHealthIndicator indicator = new EmailHealthIndicator(
                email(false), new ResendProperties(null, null, null, null));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("enabled", false);
    }

    @Test
    void enabledResendWithMissingKeyIsNotReady() {
        EmailHealthIndicator indicator = new EmailHealthIndicator(
                email(true, "resend"), new ResendProperties(null, null, null, null));

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    private static EmailProperties email(boolean enabled) {
        return email(enabled, "smtp");
    }

    private static EmailProperties email(boolean enabled, String provider) {
        return new EmailProperties(
                enabled, provider, "Coach Gym", "v1", "no-reply@example.com", "Coach Gym",
                null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254, 3, Duration.ofMinutes(15));
    }
}
