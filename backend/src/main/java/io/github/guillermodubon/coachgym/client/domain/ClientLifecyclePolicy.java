package io.github.guillermodubon.coachgym.client.domain;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.application.ClientStateConflictException;
import java.util.Objects;
import java.util.UUID;

/** Defines the explicit and side-effect-free client status transitions. */
public final class ClientLifecyclePolicy {

    private ClientLifecyclePolicy() {
    }

    public static void requireDeactivationAllowed(
            UUID clientId,
            ClientStatus currentStatus) {
        requireTransition(
                clientId,
                currentStatus,
                ClientStatus.ACTIVE,
                ClientStatus.INACTIVE);
    }

    public static void requireReactivationAllowed(
            UUID clientId,
            ClientStatus currentStatus) {
        requireTransition(
                clientId,
                currentStatus,
                ClientStatus.INACTIVE,
                ClientStatus.ACTIVE);
    }

    private static void requireTransition(
            UUID clientId,
            ClientStatus currentStatus,
            ClientStatus requiredStatus,
            ClientStatus requestedStatus) {
        Objects.requireNonNull(clientId, "Client id is required.");
        Objects.requireNonNull(currentStatus, "Current client status is required.");
        if (currentStatus != requiredStatus) {
            throw new ClientStateConflictException(
                    clientId, currentStatus, requestedStatus);
        }
    }
}
