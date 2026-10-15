package io.github.guillermodubon.coachgym.shared.identityemail;

/** Safe provider-independent outcome for one identity email submission. */
public enum IdentityEmailDeliveryStatus {
    SENT,
    FAILED,
    AMBIGUOUS
}
