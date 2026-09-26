package io.github.guillermodubon.coachgym.user;

/** Safe rejection when a requested branch conflicts with server context. */
public final class BranchResourceMismatchException extends IllegalArgumentException {

    public BranchResourceMismatchException() {
        super("The requested branch does not match the authorized branch context.");
    }
}
