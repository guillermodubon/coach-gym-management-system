package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class InactiveMembershipClientException
        extends RuntimeException {

    private final UUID clientId;

    public InactiveMembershipClientException(
            UUID clientId) {

        this(
                clientId,
                "Client "
                        + clientId
                        + " is inactive and cannot receive "
                        + "a new membership.");
    }

    public InactiveMembershipClientException(
            UUID clientId,
            String message) {

        super(message);
        this.clientId = clientId;
    }

    public UUID clientId() {
        return clientId;
    }
}
