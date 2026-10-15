package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GoogleOAuthPropertiesTest {

    @Test
    void disabledOrUnconfiguredOAuthDoesNotRequireCredentials() {
        GoogleOAuthProperties properties = properties(null, null, null, null);

        assertThat(properties.tokenUrl()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(properties.isValidWhenEnabled()).isFalse();
    }

    @Test
    void enabledOAuthRequiresCredentialsHttpsAndBoundedTiming() {
        assertThat(properties("client-id", "client-secret", "refresh-token", null)
                .isValidWhenEnabled()).isTrue();
        assertThat(properties("", "client-secret", "refresh-token", null)
                .isValidWhenEnabled()).isFalse();
        assertThat(properties("client-id", "client-secret\r\nInjected: yes", "refresh-token", null)
                .isValidWhenEnabled()).isFalse();
        assertThat(properties("client-id", "client-secret", "refresh-token",
                "http://oauth2.googleapis.com/token").isValidWhenEnabled()).isFalse();
        assertThat(properties("client-id", "client-secret", "refresh-token",
                "https://attacker.example/token").isValidWhenEnabled()).isFalse();
        assertThat(new GoogleOAuthProperties(
                "https://oauth2.googleapis.com/token", "client-id", "client-secret",
                "refresh-token", Duration.ofMinutes(1).plusMillis(1), Duration.ofSeconds(15),
                Duration.ofSeconds(60), Duration.ofSeconds(20)).isValidWhenEnabled()).isFalse();
        assertThat(new GoogleOAuthProperties(
                "https://oauth2.googleapis.com/token", "client-id", "client-secret",
                "refresh-token", Duration.ofSeconds(5), Duration.ofSeconds(15),
                Duration.ZERO, Duration.ofSeconds(20)).isValidWhenEnabled()).isFalse();
    }

    @Test
    void allowsOnlyExplicitLoopbackHttpForStubClients() {
        GoogleOAuthProperties properties = properties(
                "client-id", "client-secret", "refresh-token", "http://localhost:8080/token");

        assertThat(properties.isValidWhenEnabled()).isFalse();
        assertThat(properties.isValidForLoopbackStub()).isTrue();
        assertThat(properties.toString())
                .contains("clientSecretPresent=true", "refreshTokenPresent=true")
                .doesNotContain("client-secret", "refresh-token", "client-id");
    }

    private static GoogleOAuthProperties properties(
            String clientId, String clientSecret, String refreshToken, String tokenUrl) {
        return new GoogleOAuthProperties(
                tokenUrl, clientId, clientSecret, refreshToken,
                Duration.ofSeconds(5), Duration.ofSeconds(15),
                Duration.ofSeconds(5), Duration.ofSeconds(20));
    }
}
