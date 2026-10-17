package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import jakarta.validation.constraints.AssertTrue;
import java.net.IDN;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Provider-neutral sender identity, template, and delivery policy settings. */
@Validated
@ConfigurationProperties(prefix = "coach-gym.email")
public record EmailProperties(
        boolean enabled,
        String organizationName,
        String templateVersion,
        String fromAddress,
        String fromName,
        String replyTo,
        int maxAttachmentBytes,
        int maxSubjectLength,
        Integer maxRetryCount,
        Duration stalePendingThreshold) {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    @ConstructorBinding
    public EmailProperties {
        organizationName = defaultText(organizationName, "Coach Gym");
        templateVersion = defaultText(templateVersion, "v1");
        fromAddress = defaultText(fromAddress, "no-reply@coach-gym.local");
        fromName = defaultText(fromName, "Coach Gym");
        replyTo = blankToNull(replyTo);
        maxAttachmentBytes = maxAttachmentBytes == 0
                ? MAX_ATTACHMENT_BYTES : maxAttachmentBytes;
        maxSubjectLength = maxSubjectLength == 0 ? 200 : maxSubjectLength;
        maxRetryCount = maxRetryCount == null ? 3 : maxRetryCount;
        stalePendingThreshold = defaultDuration(stalePendingThreshold, Duration.ofMinutes(15));
    }

    /** Validates shared email settings; Gmail credentials are validated by the Gmail adapter. */
    @AssertTrue(message = "Transactional email configuration is invalid when enabled")
    public boolean isValidWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return validText(organizationName, 200)
                && templateVersion != null && VERSION.matcher(templateVersion).matches()
                && validEmail(fromAddress)
                && (fromName == null || validText(fromName, 200))
                && (replyTo == null || validEmail(replyTo))
                && maxAttachmentBytes >= 1 && maxAttachmentBytes <= MAX_ATTACHMENT_BYTES
                && maxSubjectLength >= 1 && maxSubjectLength <= 200
                && maxRetryCount >= 0 && maxRetryCount <= 10
                && positiveBounded(stalePendingThreshold, Duration.ofHours(24));
    }

    @Override
    public String toString() {
        return "EmailProperties[enabled=" + enabled
                + ", organizationNamePresent=" + hasText(organizationName)
                + ", templateVersion=" + templateVersion
                + ", fromAddressPresent=" + hasText(fromAddress)
                + ", fromNamePresent=" + hasText(fromName)
                + ", replyToPresent=" + hasText(replyTo)
                + ", maxAttachmentBytes=" + maxAttachmentBytes
                + ", maxSubjectLength=" + maxSubjectLength
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

    private static boolean validEmail(String value) {
        if (!hasText(value) || value.length() > 254 || containsLineBreak(value)
                || !EMAIL.matcher(value).matches()) {
            return false;
        }
        int at = value.lastIndexOf('@');
        try {
            return !IDN.toASCII(value.substring(at + 1)).isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
