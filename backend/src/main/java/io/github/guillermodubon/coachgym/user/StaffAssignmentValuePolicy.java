package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

final class StaffAssignmentValuePolicy {

    static final int MAX_CODE_LENGTH = 32;
    static final int MAX_REASON_LENGTH = 1_000;
    private static final int MAX_TIMEZONE_LENGTH = 64;
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9]+(?:[-_][A-Z0-9]+)*");

    private StaffAssignmentValuePolicy() {
    }

    static UUID requireId(UUID value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }

    static long requireVersion(long value, String name) {
        if (value < 0) {
            throw new StaffBranchAssignmentValidationException(name + " must not be negative");
        }
        return value;
    }

    static Instant requireInstant(Instant value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }

    static String requireReason(String value) {
        Objects.requireNonNull(value, "reason is required");
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > MAX_REASON_LENGTH
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new StaffBranchAssignmentValidationException(
                    "reason must contain between 1 and " + MAX_REASON_LENGTH + " characters");
        }
        return normalized;
    }

    static String requireCode(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.strip().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        if (!SAFE_CODE.matcher(normalized).matches()) {
            throw new StaffBranchAssignmentValidationException(name + " has an invalid format");
        }
        return normalized;
    }

    static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maxLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new StaffBranchAssignmentValidationException(name + " is outside its allowed length");
        }
        return normalized;
    }

    static String requireTimezone(String value) {
        Objects.requireNonNull(value, "timezone is required");
        String normalized = value.strip();
        if (normalized.length() > MAX_TIMEZONE_LENGTH) {
            throw new StaffBranchAssignmentValidationException("timezone is too long");
        }
        try {
            ZoneId.of(normalized);
        } catch (RuntimeException exception) {
            throw new StaffBranchAssignmentValidationException("timezone has an invalid format");
        }
        return normalized;
    }
}
