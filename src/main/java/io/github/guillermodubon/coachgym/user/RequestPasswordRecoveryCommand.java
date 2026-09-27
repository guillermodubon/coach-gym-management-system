package io.github.guillermodubon.coachgym.user;

/** Public password-recovery input; responses must remain account-independent. */
public record RequestPasswordRecoveryCommand(String email) {

    public RequestPasswordRecoveryCommand {
        email = StaffIdentityValuePolicy.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "RequestPasswordRecoveryCommand[emailPresent=" + (email != null) + ']';
    }
}
