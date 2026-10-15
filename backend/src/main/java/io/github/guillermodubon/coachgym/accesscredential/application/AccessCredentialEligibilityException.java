package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.util.UUID;

/** Indicates that a client cannot receive an active credential in its state. */
public class AccessCredentialEligibilityException extends RuntimeException {

    private final UUID clientId;
    private final ClientStatus currentStatus;

    public AccessCredentialEligibilityException(
            UUID clientId,
            ClientStatus currentStatus) {
        super("Access credential issuance is not allowed for the client state.");
        this.clientId = clientId;
        this.currentStatus = currentStatus;
    }

    public UUID clientId() {
        return clientId;
    }

    public ClientStatus currentStatus() {
        return currentStatus;
    }
}
