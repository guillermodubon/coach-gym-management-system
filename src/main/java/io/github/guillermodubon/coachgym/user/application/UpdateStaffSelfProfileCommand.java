package io.github.guillermodubon.coachgym.user.application;

/**
 * Validated replacement for the personal fields currently owned by a staff
 * account. The authenticated actor is deliberately not represented here.
 */
public record UpdateStaffSelfProfileCommand(
        String firstName,
        String lastName,
        long expectedVersion) {

    public UpdateStaffSelfProfileCommand {
        firstName = requiredText(firstName, "First name", 100);
        lastName = requiredText(lastName, "Last name", 100);
        if (expectedVersion < 0) {
            throw new StaffProfileValidationException(
                    "Expected profile version must not be negative.");
        }
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new StaffProfileValidationException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new StaffProfileValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }
}
