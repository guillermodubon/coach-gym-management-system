package io.github.guillermodubon.coachgym.user;

/** Safe provider-neutral result of submitting an invitation email. */
public enum StaffInvitationDeliveryStatus {
    SENT,
    FAILED,
    AMBIGUOUS
}
