package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import java.net.IDN;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Typed Gmail API endpoint and sender settings. */
@ConfigurationProperties(prefix = "coach-gym.email.gmail")
public record GmailApiProperties(
        String apiBaseUrl,
        String senderAddress,
        Duration connectionTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        int maxMessageBytes) {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_MESSAGE_BYTES = 12 * 1024 * 1024;

    public GmailApiProperties(
            String apiBaseUrl,
            String senderAddress,
            Duration connectionTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
        this(apiBaseUrl, senderAddress, connectionTimeout, readTimeout,
                writeTimeout, MAX_MESSAGE_BYTES);
    }

    @ConstructorBinding
    public GmailApiProperties {
        apiBaseUrl = defaultText(apiBaseUrl, "https://gmail.googleapis.com");
        senderAddress = blankToNull(senderAddress);
        connectionTimeout = defaultDuration(connectionTimeout, Duration.ofSeconds(5));
        readTimeout = defaultDuration(readTimeout, Duration.ofSeconds(15));
        writeTimeout = defaultDuration(writeTimeout, Duration.ofSeconds(15));
        maxMessageBytes = maxMessageBytes == 0 ? MAX_MESSAGE_BYTES : maxMessageBytes;
    }

    /** Validates production configuration. Loopback HTTP is reserved for tests. */
    public boolean isValidWhenEnabled() {
        return isValidWhenEnabled(false);
    }

    /**
     * JDK HttpClient exposes one whole-exchange deadline rather than separate
     * read and write deadlines. Use the stricter configured bound so neither
     * setting can be silently relaxed by the other.
     */
    Duration exchangeTimeout() {
        return readTimeout.compareTo(writeTimeout) <= 0 ? readTimeout : writeTimeout;
    }

    boolean isValidForLoopbackStub() {
        return isValidWhenEnabled(true);
    }

    private boolean isValidWhenEnabled(boolean allowLoopbackHttp) {
        return validBaseUrl(apiBaseUrl, allowLoopbackHttp, "gmail.googleapis.com")
                && validEmail(senderAddress)
                && positiveBounded(connectionTimeout, Duration.ofMinutes(1))
                && positiveBounded(readTimeout, Duration.ofMinutes(2))
                && positiveBounded(writeTimeout, Duration.ofMinutes(2))
                && maxMessageBytes >= 1_024 && maxMessageBytes <= MAX_MESSAGE_BYTES;
    }

    @Override
    public String toString() {
        return "GmailApiProperties[apiBaseUrlPresent=" + hasText(apiBaseUrl)
                + ", senderAddressPresent=" + hasText(senderAddress)
                + ", connectionTimeout=" + connectionTimeout
                + ", readTimeout=" + readTimeout
                + ", writeTimeout=" + writeTimeout
                + ", maxMessageBytes=" + maxMessageBytes + ']';
    }

    static boolean validBaseUrl(String value, boolean allowLoopbackHttp, String expectedHost) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() > 65_535 || uri.getPort() < -1) {
                return false;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return ("https".equals(scheme) && expectedHost.equalsIgnoreCase(uri.getHost()))
                    || (allowLoopbackHttp && "http".equals(scheme)
                        && isLoopbackHost(uri.getHost()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1")
                || normalized.equals("::1") || normalized.equals("[::1]");
    }

    private static boolean validEmail(String value) {
        if (!hasText(value) || value.length() > 254 || !EMAIL.matcher(value).matches()) {
            return false;
        }
        int at = value.lastIndexOf('@');
        try {
            return !IDN.toASCII(value.substring(at + 1)).isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
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
