package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.util.UUID;

/** Raised when a client lifecycle transition is incompatible with current state. */
public class ClientStateConflictException extends RuntimeException {

    private final UUID clientId;
    private final ClientStatus currentStatus;
    private final ClientStatus requestedStatus;

    public ClientStateConflictException(
            UUID clientId,
            ClientStatus currentStatus,
            ClientStatus requestedStatus) {
        super("Client status transition is not allowed from the current state.");
        this.clientId = clientId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID clientId() {
        return clientId;
    }

    public ClientStatus currentStatus() {
        return currentStatus;
    }

    public ClientStatus requestedStatus() {
        return requestedStatus;
    }
}
