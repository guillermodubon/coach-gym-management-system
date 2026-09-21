package io.github.guillermodubon.coachgym.user.application;

/** Password-strength boundaries shared by the self-service password command. */
public final class StaffPasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 256;

    private StaffPasswordPolicy() {
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new StaffProfileValidationException(field + " is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new StaffProfileValidationException(
                    field + " must not exceed " + MAX_LENGTH + " characters.");
        }
        return value;
    }

    static String newPassword(String value) {
        String normalized = required(value, "New password");
        if (normalized.length() < MIN_LENGTH) {
            throw new StaffProfileValidationException(
                    "New password does not meet the password policy.");
        }
        return normalized;
    }
}
