package io.github.guillermodubon.coachgym.notification;

/** Types currently supported by the notifications database contract. */
public enum NotificationType {
    MEMBERSHIP_EXPIRING,
    PAYMENT_VOIDED,
    PAYMENT_REFUNDED,
    INCIDENT_ASSIGNED,
    MAINTENANCE_ASSIGNED,
    SYSTEM
}
