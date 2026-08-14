package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.util.UUID;

/** Event emitted after a client has been persisted successfully. */
public record ClientRegistered(
        UUID clientId,
        String clientCode,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
