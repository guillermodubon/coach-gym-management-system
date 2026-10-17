package io.github.guillermodubon.coachgym.user;

/** Allowlisted one-time recovery input with redacted diagnostic representation. */
public record CompletePasswordRecoveryCommand(
        String token,
        String newPassword,
        String passwordConfirmation) {

    public CompletePasswordRecoveryCommand {
        token = StaffTokenPolicy.requirePresentedToken(token);
        newPassword = StaffCredentialPolicy.requirePassword(newPassword);
        StaffCredentialPolicy.requireMatchingConfirmation(newPassword, passwordConfirmation);
    }

    @Override
    public String toString() {
        return "CompletePasswordRecoveryCommand[tokenPresent=" + (token != null)
                + ", newPasswordPresent=" + (newPassword != null)
                + ", passwordConfirmationPresent=" + (passwordConfirmation != null)
                + ']';
    }
}
