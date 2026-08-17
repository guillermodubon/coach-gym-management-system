package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipClientNotFoundException
        extends RuntimeException {

    public MembershipClientNotFoundException(
            UUID clientId) {

        super(
                "Client "
                        + clientId
                        + " was not found.");
    }
}
