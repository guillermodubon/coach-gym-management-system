package io.github.guillermodubon.coachgym.shared.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Private Supabase Storage settings for the deployed shared-document profile. */
@Validated
@ConfigurationProperties(prefix = "gym.storage.supabase")
public record SupabaseStorageProperties(
        String endpoint,
        String serviceKey,
        String bucket,
        Duration connectionTimeout,
        Duration requestTimeout) {

    @ConstructorBinding
    public SupabaseStorageProperties {
        endpoint = endpoint == null ? null : endpoint.strip();
        serviceKey = serviceKey == null || serviceKey.isBlank() ? null : serviceKey.strip();
        bucket = bucket == null || bucket.isBlank() ? "coach-gym-private" : bucket.strip();
        connectionTimeout = connectionTimeout == null ? Duration.ofSeconds(5) : connectionTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }

    public boolean isValid() {
        try {
            URI uri = URI.create(endpoint == null ? "" : endpoint);
            return serviceKey != null
                    && !serviceKey.contains("\r")
                    && !serviceKey.contains("\n")
                    && uri.getScheme() != null
                    && (uri.getScheme().equalsIgnoreCase("https")
                        || "localhost".equalsIgnoreCase(uri.getHost()))
                    && uri.getHost() != null
                    && bucket.matches("[A-Za-z0-9._-]{1,63}")
                    && positiveBounded(connectionTimeout, Duration.ofMinutes(1))
                    && positiveBounded(requestTimeout, Duration.ofMinutes(2));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "SupabaseStorageProperties[endpointPresent=" + (endpoint != null)
                + ", serviceKeyPresent=" + (serviceKey != null)
                + ", bucket=" + bucket
                + ", connectionTimeout=" + connectionTimeout
                + ", requestTimeout=" + requestTimeout + ']';
    }

    private static boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }
}
