package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import java.time.Instant;
import java.util.Objects;

/** In-memory handoff used only after invitation persistence commits. */
record PreparedStaffInvitationDelivery(
        StaffInvitationRecord invitation,
        StaffInvitationEmail email,
        Instant reservedAt) {

    PreparedStaffInvitationDelivery {
        Objects.requireNonNull(invitation);
        Objects.requireNonNull(email);
        Objects.requireNonNull(reservedAt);
    }

    @Override
    public String toString() {
        return "PreparedStaffInvitationDelivery[invitationId=" + invitation.invitationId()
                + ", invitationVersion=" + invitation.version()
                + ", email=<redacted>, reservedAt=" + reservedAt + ']';
    }
}
