package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import jakarta.validation.constraints.AssertTrue;
import java.net.IDN;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Validated, provider-neutral SMTP settings. Secrets are never included in toString. */
@Validated
@ConfigurationProperties(prefix = "coach-gym.email")
public record EmailProperties(
        boolean enabled,
        String provider,
        String organizationName,
        String templateVersion,
        String fromAddress,
        String fromName,
        String replyTo,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        boolean smtpAuthEnabled,
        boolean smtpStarttlsEnabled,
        Duration connectionTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        int maxMessageBytes,
        int maxAttachmentBytes,
        int maxSubjectLength,
        int maxRecipientLength,
        Integer maxRetryCount,
        Duration stalePendingThreshold) {

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final int MAX_MESSAGE_BYTES = 12 * 1024 * 1024;

    @ConstructorBinding
    public EmailProperties {
        provider = defaultText(provider, "smtp").toLowerCase(Locale.ROOT);
        organizationName = defaultText(organizationName, "Coach Gym");
        templateVersion = defaultText(templateVersion, "v1");
        fromAddress = defaultText(fromAddress, "no-reply@coach-gym.local");
        fromName = defaultText(fromName, "Coach Gym");
        replyTo = blankToNull(replyTo);
        smtpHost = defaultText(smtpHost, "localhost");
        smtpUsername = blankToNull(smtpUsername);
        smtpPassword = blankToNull(smtpPassword);
        connectionTimeout = defaultDuration(connectionTimeout, Duration.ofSeconds(5));
        readTimeout = defaultDuration(readTimeout, Duration.ofSeconds(10));
        writeTimeout = defaultDuration(writeTimeout, Duration.ofSeconds(10));
        maxMessageBytes = maxMessageBytes == 0 ? MAX_MESSAGE_BYTES : maxMessageBytes;
        maxAttachmentBytes = maxAttachmentBytes == 0 ? 10 * 1024 * 1024 : maxAttachmentBytes;
        maxSubjectLength = maxSubjectLength == 0 ? 200 : maxSubjectLength;
        maxRecipientLength = maxRecipientLength == 0 ? 254 : maxRecipientLength;
        maxRetryCount = maxRetryCount == null ? 3 : maxRetryCount;
        stalePendingThreshold = defaultDuration(stalePendingThreshold, Duration.ofMinutes(15));
    }

    /** Backward-compatible constructor for callers that only configure SMTP limits. */
    public EmailProperties(
            boolean enabled,
            String organizationName,
            String templateVersion,
            String fromAddress,
            String fromName,
            String replyTo,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            Duration connectionTimeout,
            Duration readTimeout,
            Duration writeTimeout,
            int maxMessageBytes,
            int maxAttachmentBytes,
            int maxSubjectLength,
            int maxRecipientLength) {
        this(enabled, "smtp", organizationName, templateVersion, fromAddress, fromName, replyTo,
                smtpHost, smtpPort, smtpUsername, smtpPassword, smtpAuthEnabled,
                smtpStarttlsEnabled, connectionTimeout, readTimeout, writeTimeout,
                maxMessageBytes, maxAttachmentBytes, maxSubjectLength, maxRecipientLength,
                3, Duration.ofMinutes(15));
    }

    /** Performs the complete safe validation required when delivery is enabled. */
    @AssertTrue(message = "Transactional email configuration is invalid when enabled")
    public boolean isValidWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return (provider.equals("smtp") || provider.equals("resend"))
                && validText(organizationName, 200)
                && validVersion(templateVersion)
                && validEmail(fromAddress)
                && validOptionalHeader(fromName, 200)
                && validOptionalEmail(replyTo)
                && (provider.equals("resend") ||
                    (validHost(smtpHost)
                        && smtpPort >= 1 && smtpPort <= 65_535
                        && (!smtpAuthEnabled || (hasText(smtpUsername) && hasText(smtpPassword)))))
                && positiveBounded(connectionTimeout, Duration.ofMinutes(1))
                && positiveBounded(readTimeout, Duration.ofMinutes(2))
                && positiveBounded(writeTimeout, Duration.ofMinutes(2))
                && maxMessageBytes >= 1_024 && maxMessageBytes <= MAX_MESSAGE_BYTES
                && maxAttachmentBytes >= 1 && maxAttachmentBytes <= 10 * 1024 * 1024
                && maxSubjectLength >= 1 && maxSubjectLength <= 200
                && maxRecipientLength >= 3 && maxRecipientLength <= 254
                && maxRetryCount >= 0 && maxRetryCount <= 10
                && positiveBounded(stalePendingThreshold, Duration.ofHours(24));
    }

    @Override
    public String toString() {
        return "EmailProperties[enabled=" + enabled
                + ", provider=" + provider
                + ", organizationNamePresent=" + hasText(organizationName)
                + ", templateVersion=" + templateVersion
                + ", fromAddressPresent=" + hasText(fromAddress)
                + ", fromNamePresent=" + hasText(fromName)
                + ", replyToPresent=" + hasText(replyTo)
                + ", smtpHostPresent=" + hasText(smtpHost)
                + ", smtpPort=" + smtpPort
                + ", smtpUsernamePresent=" + hasText(smtpUsername)
                + ", smtpPasswordPresent=" + hasText(smtpPassword)
                + ", smtpAuthEnabled=" + smtpAuthEnabled
                + ", smtpStarttlsEnabled=" + smtpStarttlsEnabled
                + ", connectionTimeout=" + connectionTimeout
                + ", readTimeout=" + readTimeout
                + ", writeTimeout=" + writeTimeout
                + ", maxMessageBytes=" + maxMessageBytes
                + ", maxAttachmentBytes=" + maxAttachmentBytes
                + ", maxSubjectLength=" + maxSubjectLength
                + ", maxRecipientLength=" + maxRecipientLength
                + ", maxRetryCount=" + maxRetryCount
                + ", stalePendingThreshold=" + stalePendingThreshold
                + ']';
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

    private static boolean validText(String value, int maxLength) {
        return hasText(value) && value.length() <= maxLength && !containsLineBreak(value);
    }

    private static boolean validOptionalHeader(String value, int maxLength) {
        return value == null || validText(value, maxLength);
    }

    private static boolean validVersion(String value) {
        return value != null && VERSION.matcher(value).matches();
    }

    private static boolean validEmail(String value) {
        if (!hasText(value) || value.length() > 254 || containsLineBreak(value)
                || !EMAIL.matcher(value).matches()) {
            return false;
        }
        int at = value.lastIndexOf('@');
        try {
            String domain = IDN.toASCII(value.substring(at + 1));
            return !domain.isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validOptionalEmail(String value) {
        return value == null || validEmail(value);
    }

    private static boolean validHost(String value) {
        if (!hasText(value) || value.length() > 255 || containsLineBreak(value)) {
            return false;
        }
        return value.matches("[A-Za-z0-9._:-]+");
    }

    private static boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
