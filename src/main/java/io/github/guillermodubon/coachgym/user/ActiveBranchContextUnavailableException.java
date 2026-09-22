package io.github.guillermodubon.coachgym.user;

/**
 * Raised when a previously selected branch is no longer valid for the
 * authenticated staff member and must be selected again.
 */
public final class ActiveBranchContextUnavailableException extends IllegalStateException {

    public ActiveBranchContextUnavailableException() {
        super("The active branch context is no longer available.");
    }

    public ActiveBranchContextUnavailableException(String message) {
        super(message);
    }
}
