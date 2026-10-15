package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe delivery outcome; it contains no recipient, provider id, body, or token. */
public record StaffInvitationDeliveryAttempted(
        UUID invitationId,
        long invitationVersion,
        StaffInvitationDeliveryStatus status,
        Instant occurredAt) {
}
