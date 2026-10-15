package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Explicit browser origins allowed to call the API.
 *
 * <p>Origins are intentionally represented as an exact allow-list. Wildcard
 * origins are not compatible with credentialed session requests and are
 * rejected during application startup.</p>
 */
@Validated
@ConfigurationProperties(prefix = "coach-gym.security.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

    public CorsProperties {
        allowedOrigins = normalizeOrigins(allowedOrigins);
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }

    @AssertTrue(message = "CORS must define valid explicit origins and a positive bounded max age")
    public boolean isValid() {
        return !allowedOrigins.isEmpty()
                && allowedOrigins.stream().allMatch(CorsProperties::validOrigin)
                && !maxAge.isZero()
                && !maxAge.isNegative()
                && maxAge.compareTo(Duration.ofDays(1)) <= 0;
    }

    private static List<String> normalizeOrigins(List<String> origins) {
        if (origins == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String origin : origins) {
            if (origin == null) {
                continue;
            }
            for (String candidate : origin.split(",")) {
                String value = candidate.strip();
                if (value.endsWith("/")) {
                    value = value.substring(0, value.length() - 1);
                }
                if (!value.isBlank() && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static boolean validOrigin(String value) {
        if (value.isBlank() || value.contains("*")) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
