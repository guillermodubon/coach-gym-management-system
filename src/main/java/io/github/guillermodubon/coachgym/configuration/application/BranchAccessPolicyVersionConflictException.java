package io.github.guillermodubon.coachgym.configuration.application;

/** Raised when a branch-policy update uses a stale optimistic-lock version. */
public class BranchAccessPolicyVersionConflictException extends RuntimeException {

    public BranchAccessPolicyVersionConflictException() {
        super("Branch access policy was changed by another operation.");
    }
}
