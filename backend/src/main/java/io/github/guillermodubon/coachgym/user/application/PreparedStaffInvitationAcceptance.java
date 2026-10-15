package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationAcceptanceResult;

/** Internal post-commit handoff; never exposed as an API or logged. */
record PreparedStaffInvitationAcceptance(
        StaffInvitationAcceptanceResult result,
        StaffInvitationAccepted event) {

    @Override
    public String toString() {
        return "PreparedStaffInvitationAcceptance[resultPresent=" + (result != null)
                + ", eventPresent=" + (event != null)
                + ']';
    }
}
