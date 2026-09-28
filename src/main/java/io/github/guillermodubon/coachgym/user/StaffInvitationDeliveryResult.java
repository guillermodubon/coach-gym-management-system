package io.github.guillermodubon.coachgym.user;

import java.util.Objects;

/** Safe administrative result; it intentionally excludes all token material. */
public record StaffInvitationDeliveryResult(
        StaffInvitationDetails invitation,
        StaffInvitationDeliveryStatus deliveryStatus) {

    public StaffInvitationDeliveryResult {
        Objects.requireNonNull(invitation, "Invitation details are required.");
        Objects.requireNonNull(deliveryStatus, "Delivery status is required.");
    }
}
