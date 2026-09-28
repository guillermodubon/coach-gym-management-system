package io.github.guillermodubon.coachgym.user;

/** Allowlisted invitee input; authority and invited identity are never caller-selectable. */
public record AcceptStaffInvitationCommand(
        String token,
        String password,
        String passwordConfirmation,
        String firstName,
        String lastName) {

    public AcceptStaffInvitationCommand {
        token = StaffTokenPolicy.requirePresentedToken(token);
        password = StaffCredentialPolicy.requirePassword(password);
        StaffCredentialPolicy.requireMatchingConfirmation(password, passwordConfirmation);
        firstName = StaffIdentityValuePolicy.requireName(firstName, "First name");
        lastName = StaffIdentityValuePolicy.requireName(lastName, "Last name");
    }

    @Override
    public String toString() {
        return "AcceptStaffInvitationCommand[tokenPresent=" + (token != null)
                + ", passwordPresent=" + (password != null)
                + ", passwordConfirmationPresent=" + (passwordConfirmation != null)
                + ", firstNamePresent=" + (firstName != null)
                + ", lastNamePresent=" + (lastName != null)
                + ']';
    }
}
