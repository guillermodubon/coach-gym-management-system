package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.util.UUID;

/** Event emitted after a client has been persisted successfully. */
public record ClientRegistered(
        UUID clientId,
        String clientCode,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        UUID branchId) {

    public ClientRegistered(
            UUID clientId,
            String clientCode,
            UUID actorUserId,
            String actorIdentifier,
            Instant occurredAt) {
        this(clientId, clientCode, actorUserId, actorIdentifier, occurredAt, null);
    }

    /** Stable common accessor for branch-aware cross-module consumers. */
    public UUID homeBranchId() {
        return branchId;
    }
}
