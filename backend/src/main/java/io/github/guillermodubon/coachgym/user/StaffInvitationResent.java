package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event recording token rotation without carrying token data. */
public record StaffInvitationResent(
        UUID invitationId,
        UUID organizationId,
        UUID actorUserId,
        long version,
        Instant occurredAt) {
}
