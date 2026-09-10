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
        Instant occurredAt) {

    public enum ChangeType {
        PROFILE_UPDATED,
        DEACTIVATED,
        REACTIVATED
    }
}
