package io.github.guillermodubon.coachgym.configuration.application;

/** Indicates that the requested active canonical branch is unavailable. */
public class BranchAccessPolicyNotFoundException extends RuntimeException {

    public BranchAccessPolicyNotFoundException() {
        super("The active branch access policy is unavailable.");
    }
}
