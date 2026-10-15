package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth refresh settings. Client credentials and refresh tokens are redacted. */
@ConfigurationProperties(prefix = "coach-gym.email.gmail.oauth")
public record GoogleOAuthProperties(
        String tokenUrl,
        String clientId,
        String clientSecret,
        String refreshToken,
        Duration connectionTimeout,
        Duration requestTimeout,
        Duration expirySafetyMargin,
        Duration refreshWaitTimeout) {

    private static final int MAX_CREDENTIAL_LENGTH = 8_192;

    public GoogleOAuthProperties {
        tokenUrl = tokenUrl == null || tokenUrl.isBlank()
                ? "https://oauth2.googleapis.com/token" : tokenUrl.strip();
        clientId = blankToNull(clientId);
        clientSecret = blankToNull(clientSecret);
        refreshToken = blankToNull(refreshToken);
        connectionTimeout = defaultDuration(connectionTimeout, Duration.ofSeconds(5));
        requestTimeout = defaultDuration(requestTimeout, Duration.ofSeconds(15));
        expirySafetyMargin = defaultDuration(expirySafetyMargin, Duration.ofSeconds(60));
        refreshWaitTimeout = defaultDuration(refreshWaitTimeout, Duration.ofSeconds(20));
    }

    /** Validates production configuration. Loopback HTTP is reserved for tests. */
    public boolean isValidWhenEnabled() {
        return isValidWhenEnabled(false);
    }

    boolean isValidForLoopbackStub() {
        return isValidWhenEnabled(true);
    }

    boolean isValidWhenEnabled(boolean allowLoopbackHttp) {
        return validTokenUrl(tokenUrl, allowLoopbackHttp)
                && validCredential(clientId)
                && validCredential(clientSecret)
                && validCredential(refreshToken)
                && positiveBounded(connectionTimeout, Duration.ofMinutes(1))
                && positiveBounded(requestTimeout, Duration.ofMinutes(2))
                && positiveBounded(expirySafetyMargin, Duration.ofMinutes(5))
                && positiveBounded(refreshWaitTimeout, Duration.ofMinutes(2));
    }

    @Override
    public String toString() {
        return "GoogleOAuthProperties[tokenUrlPresent=" + hasText(tokenUrl)
                + ", clientIdPresent=" + hasText(clientId)
                + ", clientSecretPresent=" + hasText(clientSecret)
                + ", refreshTokenPresent=" + hasText(refreshToken)
                + ", connectionTimeout=" + connectionTimeout
                + ", requestTimeout=" + requestTimeout
                + ", expirySafetyMargin=" + expirySafetyMargin
                + ", refreshWaitTimeout=" + refreshWaitTimeout + ']';
    }

    private static boolean validTokenUrl(String value, boolean allowLoopbackHttp) {
        return GmailApiProperties.validBaseUrl(
                value, allowLoopbackHttp, "oauth2.googleapis.com");
    }

    private static boolean validCredential(String value) {
        return hasText(value) && value.length() <= MAX_CREDENTIAL_LENGTH
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Duration defaultDuration(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
