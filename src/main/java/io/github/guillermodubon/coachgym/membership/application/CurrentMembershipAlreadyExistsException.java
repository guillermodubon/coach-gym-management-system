package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class CurrentMembershipAlreadyExistsException
        extends RuntimeException {

    public CurrentMembershipAlreadyExistsException(
            UUID clientId) {

        super(
                "Client "
                        + clientId
                        + " already has an active or frozen membership.");
    }
}
