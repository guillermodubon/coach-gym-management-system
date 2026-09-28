package io.github.guillermodubon.coachgym.user;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StaffIdentityValuePolicy {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 1_000;
    private static final Pattern LOCAL_PART =
            Pattern.compile("[A-Z0-9.!#$%&'*+/=?{|}~-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN_LABEL =
            Pattern.compile("[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?", Pattern.CASE_INSENSITIVE);

    private StaffIdentityValuePolicy() {
    }

    public static String normalizeEmail(String value) {
        if (value == null) {
            throw new StaffIdentityValidationException("Email address is required.");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (normalized.length() > MAX_EMAIL_LENGTH
                || separator < 1
                || separator != normalized.lastIndexOf('@')
                || !isValidLocalPart(normalized.substring(0, separator))
                || !isValidDomain(normalized.substring(separator + 1))) {
            throw new StaffIdentityValidationException("Email address is invalid.");
        }
        return normalized;
    }

    static String requireName(String value, String field) {
        if (value == null || value.isBlank() || containsControl(value)) {
            throw new StaffIdentityValidationException(field + " is invalid.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new StaffIdentityValidationException(field + " is outside its allowed length.");
        }
        return normalized;
    }

    static String requireReason(String value) {
        if (value == null || value.isBlank() || containsControl(value)) {
            throw new StaffIdentityValidationException("A lifecycle reason is required.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new StaffIdentityValidationException("Lifecycle reason is outside its allowed length.");
        }
        return normalized;
    }

    public static String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return "[masked]";
        }
        return email.substring(0, 1) + "***" + email.substring(separator);
    }

    private static boolean isValidLocalPart(String value) {
        return value.length() <= MAX_LOCAL_PART_LENGTH
                && !value.startsWith(".")
                && !value.endsWith(".")
                && !value.contains("..")
                && LOCAL_PART.matcher(value).matches();
    }

    private static boolean isValidDomain(String value) {
        if (value.length() > 253) {
            return false;
        }
        String[] labels = value.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
