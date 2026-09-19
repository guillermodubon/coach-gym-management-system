package io.github.guillermodubon.coachgym.notification.infrastructure.resend;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Bounded Resend HTTPS settings. The API key is never exposed in text output. */
@Validated
@ConfigurationProperties(prefix = "coach-gym.email.resend")
public record ResendProperties(
        String apiKey,
        String endpoint,
        Duration connectionTimeout,
        Duration requestTimeout) {

    @ConstructorBinding
    public ResendProperties {
        apiKey = blankToNull(apiKey);
        endpoint = endpoint == null || endpoint.isBlank()
                ? "https://api.resend.com/emails" : endpoint.strip();
        connectionTimeout = connectionTimeout == null
                ? Duration.ofSeconds(5) : connectionTimeout;
        requestTimeout = requestTimeout == null
                ? Duration.ofSeconds(15) : requestTimeout;
    }

    public boolean isValidWhenEnabled() {
        try {
            URI uri = URI.create(endpoint);
            return apiKey != null
                    && !apiKey.contains("\r") && !apiKey.contains("\n")
                    && (uri.getScheme().equalsIgnoreCase("https")
                        || "localhost".equalsIgnoreCase(uri.getHost()))
                    && uri.getHost() != null
                    && positiveBounded(connectionTimeout, Duration.ofMinutes(1))
                    && positiveBounded(requestTimeout, Duration.ofMinutes(2));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ResendProperties[apiKeyPresent=" + (apiKey != null)
                + ", endpointPresent=" + (endpoint != null)
                + ", connectionTimeout=" + connectionTimeout
                + ", requestTimeout=" + requestTimeout + ']';
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }
}
