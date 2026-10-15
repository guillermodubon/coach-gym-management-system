package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GmailConfigurationReadinessTest {

    @Test
    void disabledDeliveryIsReadyWithoutGmailSecretsOrNetwork() {
        GmailConfigurationReadiness readiness = new GmailConfigurationReadiness(
                new GmailApiProperties(null, null, null, null, null),
                new GoogleOAuthProperties(null, null, null, null, null, null, null, null));

        assertThat(readiness.status(false))
                .isEqualTo(GmailConfigurationReadiness.Status.DISABLED);
    }

    @Test
    void enabledDeliveryRequiresValidApiAndOAuthProperties() {
        GmailConfigurationReadiness invalid = new GmailConfigurationReadiness(
                new GmailApiProperties(null, null, null, null, null),
                new GoogleOAuthProperties(null, null, null, null, null, null, null, null));
        assertThat(invalid.status(true))
                .isEqualTo(GmailConfigurationReadiness.Status.INVALID_CONFIGURATION);

        GmailConfigurationReadiness ready = new GmailConfigurationReadiness(
                new GmailApiProperties(
                        null, "coach-gym@example.test", null, null, null),
                new GoogleOAuthProperties(
                        null, "client-id", "client-secret", "refresh-token",
                        Duration.ofSeconds(5), Duration.ofSeconds(15),
                        Duration.ofSeconds(60), Duration.ofSeconds(20)));
        assertThat(ready.status(true)).isEqualTo(GmailConfigurationReadiness.Status.READY);
    }

    @Test
    void enabledDeliveryRequiresTheConfiguredFromAddressToMatchTheGmailIdentity() {
        GmailConfigurationReadiness readiness = new GmailConfigurationReadiness(
                new GmailApiProperties(
                        null, "coach-gym@example.test", null, null, null),
                new GoogleOAuthProperties(
                        null, "client-id", "client-secret", "refresh-token",
                        Duration.ofSeconds(5), Duration.ofSeconds(15),
                        Duration.ofSeconds(60), Duration.ofSeconds(20)));

        assertThat(readiness.status(true, "coach-gym@example.test"))
                .isEqualTo(GmailConfigurationReadiness.Status.READY);
        assertThat(readiness.status(true, "other@example.test"))
                .isEqualTo(GmailConfigurationReadiness.Status.INVALID_CONFIGURATION);
        assertThat(readiness.status(true, null))
                .isEqualTo(GmailConfigurationReadiness.Status.INVALID_CONFIGURATION);
    }
}
