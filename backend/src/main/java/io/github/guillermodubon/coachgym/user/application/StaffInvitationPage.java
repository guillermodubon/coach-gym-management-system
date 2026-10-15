package io.github.guillermodubon.coachgym.user.application;

import java.util.List;

/** One bounded page of invitation metadata, never including tokens. */
public record StaffInvitationPage(List<StaffInvitationRecord> invitations, boolean hasNext) {

    public StaffInvitationPage {
        invitations = List.copyOf(invitations);
    }
}
