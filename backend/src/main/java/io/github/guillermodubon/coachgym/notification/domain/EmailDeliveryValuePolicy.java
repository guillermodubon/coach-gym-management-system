package io.github.guillermodubon.coachgym.notification.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure normalization and bounded-value policy shared by email contracts. */
public final class EmailDeliveryValuePolicy {

    public static final int MAX_RECIPIENT_LENGTH = 254;
    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_FAILURE_MESSAGE_LENGTH = 500;
    public static final int MAX_TEMPLATE_VERSION_LENGTH = 32;
    public static final int MAX_FILENAME_LENGTH = 200;
    public static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern TEMPLATE_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private EmailDeliveryValuePolicy() {
    }

    public static String normalizeRecipient(String value) {
        String normalized = required(value, "Email recipient", MAX_RECIPIENT_LENGTH);
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw invalid("Email recipient must be valid.");
        }
        return normalized;
    }

    public static String maskRecipient(String value) {
        String normalized = normalizeRecipient(value);
        int at = normalized.indexOf('@');
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        String visible = local.length() <= 1 ? "*" : local.substring(0, 1);
        return visible + "***@" + domain;
    }

    public static String normalizeAddress(String value, String field) {
        return normalizeRecipient(required(value, field, MAX_RECIPIENT_LENGTH));
    }

    public static String normalizeOptionalAddress(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeAddress(value, field);
    }

    public static String normalizeSubject(String value) {
        String normalized = required(value, "Email subject", MAX_SUBJECT_LENGTH);
        rejectHeaderInjection(normalized, "Email subject");
        return normalized;
    }

    public static String normalizeTemplateVersion(String value) {
        String normalized = required(value, "Email template version", MAX_TEMPLATE_VERSION_LENGTH);
        if (!TEMPLATE_VERSION.matcher(normalized).matches()) {
            throw invalid("Email template version contains unsupported characters.");
        }
        return normalized;
    }

    public static String normalizeFilename(String value) {
        String normalized = required(value, "Email attachment filename", MAX_FILENAME_LENGTH);
        if (normalized.contains("/") || normalized.contains("\\")
                || normalized.equals(".") || normalized.equals("..")
                || normalized.contains("..")) {
            throw invalid("Email attachment filename must be a safe basename.");
        }
        rejectHeaderInjection(normalized, "Email attachment filename");
        return normalized;
    }

    public static String normalizeChecksum(String value, String field) {
        if (value == null || !SHA256.matcher(value.strip()).matches()) {
            throw invalid(field + " must be a SHA-256 hexadecimal value.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeDigest(String value) {
        return normalizeChecksum(value, "Email idempotency digest");
    }

    public static String normalizeFailureMessage(String value) {
        String normalized = required(value, "Email failure message", MAX_FAILURE_MESSAGE_LENGTH);
        rejectHeaderInjection(normalized, "Email failure message");
        return normalized;
    }

    public static String normalizeOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid(field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    public static void rejectHeaderInjection(String value, String field) {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw invalid(field + " must not contain line breaks.");
        }
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid(field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static EmailDeliveryValidationException invalid(String message) {
        return new EmailDeliveryValidationException(message);
    }
}
