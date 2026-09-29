package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GmailApiPropertiesTest {

    @Test
    void defaultsToGoogleHttpsEndpointAndBoundedTimeouts() {
        GmailApiProperties properties = new GmailApiProperties(
                null, "coachgym.demo@gmail.com", null, null, null);

        assertThat(properties.apiBaseUrl()).isEqualTo("https://gmail.googleapis.com");
        assertThat(properties.isValidWhenEnabled()).isTrue();
        assertThat(properties.toString()).doesNotContain("coachgym.demo@gmail.com");
    }

    @Test
    void rejectsMissingSenderInvalidUrlAndUnboundedTimeouts() {
        assertThat(api(null, "https://gmail.googleapis.com", Duration.ofSeconds(5))
                .isValidWhenEnabled()).isFalse();
        assertThat(api("sender@example.test", "http://gmail.googleapis.com", Duration.ofSeconds(5))
                .isValidWhenEnabled()).isFalse();
        assertThat(api("sender@example.test", "https://user:password@gmail.googleapis.com",
                Duration.ofSeconds(5)).isValidWhenEnabled()).isFalse();
        assertThat(api("sender@example.test", "https://attacker.example", Duration.ofSeconds(5))
                .isValidWhenEnabled()).isFalse();
        assertThat(api("sender@example.test", "https://gmail.googleapis.com?redirect=elsewhere",
                Duration.ofSeconds(5)).isValidWhenEnabled()).isFalse();
        assertThat(api("sender@example.test", "https://gmail.googleapis.com",
                Duration.ofMinutes(1).plusMillis(1)).isValidWhenEnabled()).isFalse();
    }

    @Test
    void permitsHttpOnlyForLoopbackStubs() {
        GmailApiProperties properties = api(
                "sender@example.test", "http://127.0.0.1:8080", Duration.ofSeconds(5));

        assertThat(properties.isValidWhenEnabled()).isFalse();
        assertThat(properties.isValidForLoopbackStub()).isTrue();
    }

    @Test
    void boundsConfiguredMimeMessageSizeToTheExistingTwelveMibLimit() {
        GmailApiProperties defaultLimit = new GmailApiProperties(
                "https://gmail.googleapis.com", "sender@example.test",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(15));
        GmailApiProperties tooSmall = new GmailApiProperties(
                "https://gmail.googleapis.com", "sender@example.test",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(15), 1_023);
        GmailApiProperties tooLarge = new GmailApiProperties(
                "https://gmail.googleapis.com", "sender@example.test",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(15),
                12 * 1024 * 1024 + 1);

        assertThat(defaultLimit.maxMessageBytes()).isEqualTo(12 * 1024 * 1024);
        assertThat(defaultLimit.isValidWhenEnabled()).isTrue();
        assertThat(tooSmall.isValidWhenEnabled()).isFalse();
        assertThat(tooLarge.isValidWhenEnabled()).isFalse();
    }

    private static GmailApiProperties api(String sender, String endpoint, Duration connectTimeout) {
        return new GmailApiProperties(
                endpoint, sender, connectTimeout, Duration.ofSeconds(15), Duration.ofSeconds(15));
    }
}
