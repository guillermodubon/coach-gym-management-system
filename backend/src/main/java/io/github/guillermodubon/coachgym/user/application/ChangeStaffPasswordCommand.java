package io.github.guillermodubon.coachgym.user.application;

/**
 * Validated password-change input. Password values are never included in the
 * command's string representation or in any public response contract.
 */
public record ChangeStaffPasswordCommand(
        String currentPassword,
        String newPassword,
        String newPasswordConfirmation) {

    public ChangeStaffPasswordCommand {
        currentPassword = StaffPasswordPolicy.required(currentPassword, "Current password");
        newPassword = StaffPasswordPolicy.newPassword(newPassword);
        newPasswordConfirmation = StaffPasswordPolicy.required(
                newPasswordConfirmation, "New password confirmation");
        if (!newPassword.equals(newPasswordConfirmation)) {
            throw new StaffProfileValidationException(
                    "New password confirmation does not match.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new StaffProfileValidationException(
                    "New password must differ from the current password.");
        }
    }

    @Override
    public String toString() {
        return "ChangeStaffPasswordCommand[currentPasswordPresent="
                + (currentPassword != null)
                + ", newPasswordPresent=" + (newPassword != null)
                + ", confirmationPresent=" + (newPasswordConfirmation != null)
                + "]";
    }
}
