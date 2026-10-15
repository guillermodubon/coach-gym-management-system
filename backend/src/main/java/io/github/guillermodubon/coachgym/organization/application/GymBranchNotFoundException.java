package io.github.guillermodubon.coachgym.organization.application;

import java.util.UUID;

/** Indicates that a canonical branch is not available. */
public class GymBranchNotFoundException extends RuntimeException {

    public GymBranchNotFoundException(UUID id) {
        super("The requested gym branch was not found.");
    }
}
