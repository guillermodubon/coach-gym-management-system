package io.github.guillermodubon.coachgym.user;

/** Raised when an invitation does not exist in the actor's organization. */
public final class StaffInvitationNotFoundException extends RuntimeException {

    public StaffInvitationNotFoundException() {
        super("Staff invitation was not found.");
    }
}
