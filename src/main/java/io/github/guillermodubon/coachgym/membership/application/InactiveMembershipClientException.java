package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class InactiveMembershipClientException
        extends RuntimeException {

    public InactiveMembershipClientException(
            UUID clientId) {

        super(
                "Client "
                        + clientId
                        + " is inactive and cannot receive "
                        + "a new membership.");
    }
}
