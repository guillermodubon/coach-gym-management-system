package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event recording immediate invitation token invalidation. */
public record StaffInvitationRevoked(
        UUID invitationId,
        UUID organizationId,
        UUID actorUserId,
        long version,
        Instant occurredAt) {
}
