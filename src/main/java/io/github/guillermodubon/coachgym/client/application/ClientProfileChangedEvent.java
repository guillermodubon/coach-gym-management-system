package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.time.Instant;
import java.util.UUID;

/** Minimal event used by audit consumers without exposing personal field values. */
public record ClientProfileChangedEvent(
        UUID clientId,
        ChangeType changeType,
        ClientStatus previousStatus,
        ClientStatus newStatus,
        UUID changedByUserId,
        Instant occurredAt,
        UUID homeBranchId) {

    public ClientProfileChangedEvent(
            UUID clientId,
            ChangeType changeType,
            ClientStatus previousStatus,
            ClientStatus newStatus,
            UUID changedByUserId,
            Instant occurredAt) {
        this(clientId, changeType, previousStatus, newStatus, changedByUserId,
                occurredAt, null);
    }

    public UUID branchId() {
        return homeBranchId;
    }

    public enum ChangeType {
        PROFILE_UPDATED,
        DEACTIVATED,
        REACTIVATED
    }
}
