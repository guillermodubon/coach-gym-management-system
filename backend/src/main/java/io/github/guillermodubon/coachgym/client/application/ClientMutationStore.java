package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;

/** Write port for client profile and lifecycle mutations. */
public interface ClientMutationStore {

    void update(
            UUID clientId,
            UpdateClientCommand command,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId);

    default void update(
            UUID clientId,
            UpdateClientCommand command,
            AuthenticatedActor actor,
            Instant occurredAt) {
        update(clientId, command, actor, occurredAt, null);
    }

    void changeStatus(
            UUID clientId,
            ClientStatus expectedCurrentStatus,
            ClientStatus requestedStatus,
            String reason,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId);

    default void changeStatus(
            UUID clientId,
            ClientStatus expectedCurrentStatus,
            ClientStatus requestedStatus,
            String reason,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        changeStatus(clientId, expectedCurrentStatus, requestedStatus, reason,
                expectedVersion, actor, occurredAt, null);
    }
}
