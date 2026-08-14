package io.github.guillermodubon.coachgym.client.application;

import java.util.UUID;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID id) {
        super("Client with id '" + id + "' was not found.");
    }
}
