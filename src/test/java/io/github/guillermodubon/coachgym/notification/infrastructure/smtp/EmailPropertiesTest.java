package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailPropertiesTest {

    @Test
    void disabledDeliveryDoesNotRequireSecrets() {
        assertThat(properties(false, false, null, null).isValidWhenEnabled()).isTrue();
    }

    @Test
    void enabledDeliveryRequiresSafeBoundedConfiguration() {
        assertThat(properties(true, false, null, null).isValidWhenEnabled()).isTrue();
        assertThat(properties(true, true, "smtp-user", "smtp-password").isValidWhenEnabled()).isTrue();
        assertThat(properties(true, true, "", "").isValidWhenEnabled()).isFalse();
        assertThat(new EmailProperties(true, "Coach Gym", "v1", "invalid", "Coach Gym", null,
                "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254).isValidWhenEnabled()).isFalse();
    }

    @Test
    void rejectsInvalidPortsTimeoutsAndLimits() {
        EmailProperties base = properties(true, false, null, null);
        assertThat(new EmailProperties(true, base.organizationName(), base.templateVersion(),
                base.fromAddress(), base.fromName(), base.replyTo(), base.smtpHost(), 0,
                null, null, false, false, base.connectionTimeout(), base.readTimeout(),
                base.writeTimeout(), base.maxMessageBytes(), base.maxAttachmentBytes(),
                base.maxSubjectLength(), base.maxRecipientLength()).isValidWhenEnabled()).isFalse();
        assertThat(new EmailProperties(true, base.organizationName(), base.templateVersion(),
                base.fromAddress(), base.fromName(), base.replyTo(), base.smtpHost(), 65_536,
                null, null, false, false, base.connectionTimeout(), base.readTimeout(),
                base.writeTimeout(), base.maxMessageBytes(), base.maxAttachmentBytes(),
                base.maxSubjectLength(), base.maxRecipientLength()).isValidWhenEnabled()).isFalse();
        assertThat(new EmailProperties(true, base.organizationName(), base.templateVersion(),
                base.fromAddress(), base.fromName(), base.replyTo(), base.smtpHost(), base.smtpPort(),
                null, null, false, false, Duration.ZERO, base.readTimeout(), base.writeTimeout(),
                base.maxMessageBytes(), base.maxAttachmentBytes(), base.maxSubjectLength(),
                base.maxRecipientLength()).isValidWhenEnabled()).isFalse();
    }

    @Test
    void toStringNeverExposesSmtpPassword() {
        EmailProperties properties = properties(true, true, "user", "super-secret-password");
        assertThat(properties.toString())
                .contains("smtpPasswordPresent=true")
                .doesNotContain("super-secret-password");
    }

    private static EmailProperties properties(
            boolean enabled, boolean authEnabled, String username, String password) {
        return new EmailProperties(enabled, "Coach Gym", "v1", "no-reply@coach-gym.local",
                "Coach Gym", null, "localhost", 1025, username, password, authEnabled, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254);
    }
}
