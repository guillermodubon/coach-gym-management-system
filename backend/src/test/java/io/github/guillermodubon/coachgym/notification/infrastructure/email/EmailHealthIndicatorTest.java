package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailApiProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailConfigurationReadiness;
import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GoogleOAuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailHealthIndicatorTest {

    @Test
    void disabledEmailIsUpWithoutProviderSecrets() {
        EmailHealthIndicator indicator = new EmailHealthIndicator(
                email(false, "no-reply@example.test"), readiness(true));

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(indicator.health().getDetails())
                .containsEntry("enabled", false)
                .containsEntry("configuration", "disabled")
                .doesNotContainKey("provider");
    }

    @Test
    void enabledReadinessRequiresValidGmailOAuthAndMatchingSender() {
        EmailHealthIndicator valid = new EmailHealthIndicator(
                email(true, "no-reply@example.test"), readiness(true));
        EmailHealthIndicator invalid = new EmailHealthIndicator(
                email(true, "different@example.test"), readiness(true));
        EmailHealthIndicator invalidOAuth = new EmailHealthIndicator(
                email(true, "no-reply@example.test"), readiness(false));

        assertThat(valid.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(invalid.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(invalidOAuth.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(valid.health().getDetails())
                .containsEntry("configuration", "ready");
        assertThat(invalidOAuth.health().getDetails())
                .containsEntry("configuration", "invalid")
                .containsEntry("reason", "configuration_invalid");
        assertThat(invalidOAuth.health().getDetails().toString())
                .doesNotContain("synthetic-client-id", "synthetic-client-secret",
                        "synthetic-refresh-token", "coach-gym@example.test");
    }

    private static EmailProperties email(boolean enabled, String fromAddress) {
        return new EmailProperties(
                enabled, "Coach Gym", "v1", fromAddress, "Coach Gym", null,
                10 * 1024 * 1024, 200, 3, Duration.ofMinutes(15));
    }

    private static GmailConfigurationReadiness readiness(boolean valid) {
        return new GmailConfigurationReadiness(
                new GmailApiProperties(
                        "https://gmail.googleapis.com", "no-reply@example.test",
                        null, null, null),
                new GoogleOAuthProperties(
                        null,
                        valid ? "synthetic-client-id" : null,
                        valid ? "synthetic-client-secret" : null,
                        valid ? "synthetic-refresh-token" : null,
                        null, null, null, null));
    }
}
