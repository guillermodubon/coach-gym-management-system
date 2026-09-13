package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Indicates that a requested credential or owning client does not exist. */
public class AccessCredentialNotFoundException extends RuntimeException {

    private final UUID identifier;

    public AccessCredentialNotFoundException(UUID identifier) {
        super("Access credential resource was not found.");
        this.identifier = identifier;
    }

    public UUID identifier() {
        return identifier;
    }
}
