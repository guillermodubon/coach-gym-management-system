package io.github.guillermodubon.coachgym.user;

/** Safe denial for a branch-owned resource without existence details. */
public final class BranchResourceAuthorizationException extends IllegalStateException {

    public BranchResourceAuthorizationException() {
        super("The branch resource is not available to the authenticated staff member.");
    }
}
