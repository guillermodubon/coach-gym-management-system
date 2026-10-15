package io.github.guillermodubon.coachgym.user;

import java.util.List;

/** Safe bounded invitation page returned to administrative callers. */
public record StaffInvitationPageDetails(List<StaffInvitationDetails> invitations, boolean hasNext) {

    public StaffInvitationPageDetails {
        invitations = List.copyOf(invitations);
    }
}
