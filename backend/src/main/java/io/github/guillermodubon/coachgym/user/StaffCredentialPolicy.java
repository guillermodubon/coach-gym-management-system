package io.github.guillermodubon.coachgym.user;

/** Framework-neutral password bounds shared by invitation and recovery commands. */
public final class StaffCredentialPolicy {

    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 256;

    private StaffCredentialPolicy() {
    }

    public static String requirePassword(String value) {
        if (value == null
                || value.isBlank()
                || value.length() < MIN_PASSWORD_LENGTH
                || value.length() > MAX_PASSWORD_LENGTH) {
            throw new StaffIdentityValidationException("Password does not meet the password policy.");
        }
        return value;
    }

    public static void requireMatchingConfirmation(String password, String confirmation) {
        if (password == null || confirmation == null || !password.equals(confirmation)) {
            throw new StaffIdentityValidationException("Password confirmation does not match.");
        }
    }
}
