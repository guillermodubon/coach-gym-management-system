package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Indicates that the client targeted by credential issuance does not exist. */
public class AccessCredentialClientNotFoundException extends RuntimeException {

    private final UUID clientId;

    public AccessCredentialClientNotFoundException(UUID clientId) {
        super("Access credential client was not found.");
        this.clientId = clientId;
    }

    public UUID clientId() {
        return clientId;
    }
}
