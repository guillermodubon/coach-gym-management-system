package io.github.guillermodubon.coachgym.organization;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Package-private normalization shared by the public organization contracts. */
final class OrganizationValuePolicy {

    static final int MAX_CODE_LENGTH = 32;
    static final int MAX_ORGANIZATION_NAME_LENGTH = 255;
    static final int MAX_DISPLAY_NAME_LENGTH = 160;
    static final int MAX_BRANCH_NAME_LENGTH = 160;
    static final int MAX_ADDRESS_LENGTH = 200;
    static final int MAX_CITY_LENGTH = 120;
    static final int MAX_STATE_LENGTH = 120;
    static final int MAX_POSTAL_CODE_LENGTH = 32;
    static final int MAX_PHONE_LENGTH = 32;
    static final int MAX_EMAIL_LENGTH = 254;
    static final int MAX_REASON_LENGTH = 1000;
    static final int MAX_TIMEZONE_LENGTH = 64;

    private static final Pattern CODE = Pattern.compile(
            "[A-Z0-9]+(?:[-_][A-Z0-9]+)*");
    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");
    private static final Set<String> ISO_CURRENCIES = Currency
            .getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private OrganizationValuePolicy() {
    }

    static String code(String value, String field) {
        String normalized = required(value, field, MAX_CODE_LENGTH);
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new OrganizationValidationException(
                    field + " must contain only letters, digits, hyphens, or underscores.");
        }
        return normalized;
    }

    static String organizationName(String value, String field) {
        return required(value, field, MAX_ORGANIZATION_NAME_LENGTH);
    }

    static String displayName(String value, String field) {
        return required(value, field, MAX_DISPLAY_NAME_LENGTH);
    }

    static String branchName(String value) {
        return required(value, "Branch name", MAX_BRANCH_NAME_LENGTH);
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field, maxLength);
    }

    static String email(String value, String field) {
        String normalized = optionalText(value, field, MAX_EMAIL_LENGTH);
        if (normalized == null) {
            return null;
        }
        if (!EMAIL.matcher(normalized).matches()) {
            throw new OrganizationValidationException(field + " format is invalid.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    static String phone(String value, String field) {
        return optionalText(value, field, MAX_PHONE_LENGTH);
    }

    static String timezone(String value, boolean optional) {
        String normalized = optional
                ? optionalText(value, "Branch timezone", MAX_TIMEZONE_LENGTH)
                : required(value, "Organization timezone", MAX_TIMEZONE_LENGTH);
        if (normalized == null) {
            return null;
        }
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (RuntimeException exception) {
            throw new OrganizationValidationException(
                    "Timezone must be a valid IANA timezone identifier.");
        }
    }

    static String currency(String value) {
        String normalized = required(value, "Organization currency", 3)
                .toUpperCase(Locale.ROOT);
        if (normalized.length() != 3 || !ISO_CURRENCIES.contains(normalized)) {
            throw new OrganizationValidationException(
                    "Organization currency must be a supported ISO 4217 code.");
        }
        return normalized;
    }

    static String countryCode(String value) {
        String normalized = optionalText(value, "Branch country code", 2);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODE.matcher(normalized).matches()) {
            throw new GymBranchValidationException(
                    "Branch country code must be a two-letter ISO 3166 code.");
        }
        return normalized;
    }

    static String reason(String value) {
        return required(value, "Branch status-change reason", MAX_REASON_LENGTH);
    }

    static long version(long value, String field) {
        if (value < 0) {
            throw new OrganizationValidationException(field + " must not be negative.");
        }
        return value;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new OrganizationValidationException(field + " is required.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new OrganizationValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new OrganizationValidationException(
                    field + " must not contain control characters.");
        }
        return normalized;
    }
}
